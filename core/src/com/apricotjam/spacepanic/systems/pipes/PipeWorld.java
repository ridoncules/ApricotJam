package com.apricotjam.spacepanic.systems.pipes;

import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.art.HelmetUI;
import com.apricotjam.spacepanic.art.PipeGameArt;
import com.apricotjam.spacepanic.art.PipeGameArt.RotatedAnimationData;
import com.apricotjam.spacepanic.art.Shaders;
import com.apricotjam.spacepanic.components.AnimationComponent;
import com.apricotjam.spacepanic.components.BitmapFontComponent;
import com.apricotjam.spacepanic.components.ClickComponent;
import com.apricotjam.spacepanic.components.ColorInterpolationComponent;
import com.apricotjam.spacepanic.components.ComponentMappers;
import com.apricotjam.spacepanic.components.FBO_Component;
import com.apricotjam.spacepanic.components.FBO_ItemComponent;
import com.apricotjam.spacepanic.components.ShaderComponent;
import com.apricotjam.spacepanic.components.ShaderTimeComponent;
import com.apricotjam.spacepanic.components.StateComponent;
import com.apricotjam.spacepanic.components.TextureComponent;
import com.apricotjam.spacepanic.components.TickerComponent;
import com.apricotjam.spacepanic.components.TransformComponent;
import com.apricotjam.spacepanic.components.TweenComponent;
import com.apricotjam.spacepanic.components.TweenSpec;
import com.apricotjam.spacepanic.components.pipe.PipeComponent;
import com.apricotjam.spacepanic.components.pipe.PipeFluidComponent;
import com.apricotjam.spacepanic.components.pipe.PipeTileComponent;
import com.apricotjam.spacepanic.gameelements.Resource;
import com.apricotjam.spacepanic.interfaces.ClickInterface;
import com.apricotjam.spacepanic.interfaces.EventInterface;
import com.apricotjam.spacepanic.interfaces.TweenInterface;
import com.apricotjam.spacepanic.misc.Colors;
import com.apricotjam.spacepanic.screen.BasicScreen;
import com.apricotjam.spacepanic.systems.RenderingSystem;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class PipeWorld {
	static public final int GRID_LENGTH = 5;
	static public final Array<GridPoint2> GridDeltas = createGridDeltas();
	static public float PIPE_Z = 0f;
	
	private PipePuzzleGenerator generator = new PipePuzzleGenerator();
	private RandomXS128 rng = new RandomXS128(0);
	
	private Entity masterEntity;
	private Entity timer;
	
	private Array<Entity> entryPipes = new Array<Entity>();
	private Array<Entity> exitPipes = new Array<Entity>();
	
	private int difficulty;
	
	private enum TileType {CORNER, SIDE, CENTRE};
	
	public PipeWorld(Entity masterEntity, int difficulty) {
		this.masterEntity = masterEntity;
		this.difficulty = difficulty;
	}
	
	public void build(Engine engine) {
		// Create fluid lighting fbo.
		engine.addEntity(createFluidFBO());
		
		// Generate puzzle.
		generator.generatePuzzle(difficulty%10, 1 + (int)(difficulty/10f));
		
		byte[][] maskGrid = generator.getMaskGrid();
		
		Entity[][] pipeEntities = new Entity[GRID_LENGTH][GRID_LENGTH];
		
		// Create puzzle pipes and fluid.
		for (int i = 0; i < GRID_LENGTH; ++i) {
			for (int j = 0; j < GRID_LENGTH; ++j) {
				Entity pipe = createPipe(maskGrid[i][j], i, j, true);
				
				if (rng.nextBoolean())
					rotateTile(pipe);
				else {
					rotateTile(pipe);
					rotateTile(pipe);
					rotateTile(pipe);
				}
								
				engine.addEntity(pipe);
				
				pipeEntities[i][j] = pipe;
			}
		}
		
		// Create circuit borders.
		for (int i = -1; i < GRID_LENGTH + 1; ++i) {
			engine.addEntity(createCircuitBorder(i, -1, false));
			engine.addEntity(createCircuitBorder(i, GRID_LENGTH, false));
		}
		for (int j = 0; j < GRID_LENGTH; ++j) {
			engine.addEntity(createCircuitBorder(-2, j, false));
			engine.addEntity(createCircuitBorder(GRID_LENGTH + 1, j, false));
		}
		engine.addEntity(createCircuitBorder(-2, -1, true));
		engine.addEntity(createCircuitBorder(-2, GRID_LENGTH, true));
		engine.addEntity(createCircuitBorder(GRID_LENGTH + 1, -1, true));
		engine.addEntity(createCircuitBorder(GRID_LENGTH + 1, GRID_LENGTH, true));
		
		// Create pipe background tiles.
		for (int i = 0; i < GRID_LENGTH; ++i) {
			for (int j = 0; j < GRID_LENGTH; ++j) {
				engine.addEntity(createPipeBG(i, j));
			}
		}
		
		// Link up puzzle neighbours.
		for (int i = 0; i < GRID_LENGTH; ++i) {
			for (int j = 0; j < GRID_LENGTH; ++j) {
				for (int idir = 0; idir < 4; ++idir) {
					int i_child = i + GridDeltas.get(idir).x;
					int j_child = j + GridDeltas.get(idir).y;
					
					if (withinBounds(i_child, j_child)) {
						PipeTileComponent pipeTileComp = ComponentMappers.pipetile.get(pipeEntities[i][j]);
						pipeTileComp.neighbours[idir] = pipeEntities[i_child][j_child];
					}
				}
			}
		}
		
		buildTimerPipes(engine, pipeEntities);
		
		// Create exit pipe.
		Array<GridPoint2> exits = generator.getExitPoints();
		
		for (int iexit = 0; iexit < exits.size; ++iexit) {
			Entity exitPipe = createPipe((byte)(8), exits.get(iexit).x, exits.get(iexit).y, false);
			PipeTileComponent exitPipeTileComp = ComponentMappers.pipetile.get(pipeEntities[exits.get(iexit).x - 1][exits.get(iexit).y]);
			exitPipeTileComp.neighbours[1] = exitPipe;
			engine.addEntity(exitPipe);
			exitPipes.add(exitPipe);
		}
		
		// Create back panel.
		engine.addEntity(createBackPanel());
		
		// Create timer.
		timer = createTimer(30);
		engine.addEntity(timer);
		
		// Create led display.
		//engine.addEntity(createLED_Panel());
		
		// Create capsule parts.
		engine.addEntity(createCapsulePart(true));
		engine.addEntity(createCapsulePart(false));
		engine.addEntity(createCapsuleMaskPart(true));
		engine.addEntity(createCapsuleMaskPart(false));
	}
	
	private void buildTimerPipes(Engine engine, Entity[][] pipeGrid) {
		Array<GridPoint2> starts = generator.getEntryPoints();
		
		int timer_i = -1;
		Array<Integer> timer_j = new Array<Integer>();
		timer_j.add(0);
		
		if (starts.size > 1) {
			if (starts.size < 4)
				timer_j.add(GRID_LENGTH - 1);
			else
				timer_j.add(GRID_LENGTH - 2);
			
			if (starts.size > 2) {
				timer_j.add(GRID_LENGTH - 3);
				
				if (starts.size == 4)
					timer_j.add(GRID_LENGTH - 1);
			}
		}
			
		for (int istart = 0; istart < starts.size; ++istart) {
			int verticalDirection = starts.get(istart).y - timer_j.get(istart);
			if (verticalDirection > 0) {
				Entity startPipe = createPipe((byte)(1), timer_i, timer_j.get(istart), false);
				entryPipes.add(startPipe);
				Entity startFluid = createFluid(startPipe, 2);
				PipeTileComponent parentPipeTileComp = ComponentMappers.pipetile.get(startPipe);
				
				engine.addEntity(startPipe);
				//engine.addEntity(startFluid);
				
				for (int j = timer_j.get(istart) + 1; j <= starts.get(istart).y; ++j) {
					byte mask = (byte)((j == starts.get(istart).y) ? 6 : 5);
					Entity timerPipe = createPipe(mask, timer_i, j, false);
					
					parentPipeTileComp.neighbours[0] = timerPipe;
					parentPipeTileComp = ComponentMappers.pipetile.get(timerPipe);
					
					engine.addEntity(timerPipe);
				}
				
				parentPipeTileComp.neighbours[1] = pipeGrid[0][starts.get(istart).y];
			}
			else if (verticalDirection < 0) {
				Entity startPipe = createPipe((byte)(4), timer_i, timer_j.get(istart), false);
				entryPipes.add(startPipe);
				Entity startFluid = createFluid(startPipe, 0);
				PipeTileComponent parentPipeTileComp = ComponentMappers.pipetile.get(startPipe);
				
				engine.addEntity(startPipe);
				//engine.addEntity(startFluid);
				
				for (int j = timer_j.get(istart) - 1; j >= starts.get(istart).y; --j) {
					byte mask = (byte)((j == starts.get(istart).y) ? 3 : 5);
					Entity timerPipe = createPipe(mask, timer_i, j, false);
					
					parentPipeTileComp.neighbours[2] = timerPipe;
					parentPipeTileComp = ComponentMappers.pipetile.get(timerPipe);
					
					engine.addEntity(timerPipe);
				}
				
				parentPipeTileComp.neighbours[1] = pipeGrid[0][starts.get(istart).y];
			}
			else {
				Entity startPipe = createPipe((byte)(2), timer_i, timer_j.get(istart), false);
				entryPipes.add(startPipe);
				Entity startFluid = createFluid(startPipe, 3);
				PipeTileComponent parentPipeTileComp = ComponentMappers.pipetile.get(startPipe);
				
				engine.addEntity(startPipe);
				//engine.addEntity(startFluid);
				
				parentPipeTileComp.neighbours[1] = pipeGrid[0][starts.get(istart).y];
			}
		}
	}
	
	public Array<Entity> getEntryPipes() {
		return entryPipes;
	}
	
	public Array<Entity> getExitPipes() {
		return exitPipes;
	}
	
	public Array<GridPoint2> getEntryPoints() {
		return generator.getEntryPoints();
	}
	
	public Array<GridPoint2> getExitPoints() {
		return generator.getExitPoints();
	}
	
	public Entity getTimer() {
		return timer;
	}
	
	private Entity createCapsulePart(boolean isLeft) {
		Entity entity = new Entity();
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.region = isLeft ? PipeGameArt.capsuleLeft : PipeGameArt.capsuleRight;
		textureComp.size.set((260f/896f)*7f, 7f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		float halfwidth = (GRID_LENGTH + 4f) / 2f + 1f;
		transComp.position.set(isLeft ? -halfwidth : halfwidth, 0, PIPE_Z);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);
		
		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("light");
		entity.add(shaderComp);
		
		return entity;
	}
	
	private Entity createCapsuleMaskPart(boolean isLeft) {
		Entity entity = new Entity();
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.region = isLeft ? PipeGameArt.capsuleMaskLeft : PipeGameArt.capsuleMaskRight;
		textureComp.size.set((260f/896f)*7f, 7f);
		textureComp.color.set(0f, 0f, 0f, 1f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		float halfwidth = (GRID_LENGTH + 4f) / 2f + 1f;
		transComp.position.set(isLeft ? -halfwidth : halfwidth, 0, PIPE_Z);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);
		
		ColorInterpolationComponent colorInterpComp = new ColorInterpolationComponent();
		colorInterpComp.start.set(0f, 0f, 0f, 1f);
		colorInterpComp.finish.set(HelmetUI.resourceColors.get(Resource.OXYGEN));
		entity.add(colorInterpComp);
		
		TweenComponent tweenComp = new TweenComponent();
		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0.0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 2f;
		tweenSpec.reverse = true;
		tweenSpec.cycle = TweenSpec.Cycle.INFLOOP;
		tweenSpec.interp = Interpolation.sine;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				TextureComponent tc = ComponentMappers.texture.get(e);
				ColorInterpolationComponent cic = ComponentMappers.colorinterps.get(e);
				Colors.lerp(tc.color, cic.start, cic.finish, a);
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		entity.add(tweenComp);
		
		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("light");
		entity.add(shaderComp);
		
		return entity;
	}
	
	private Entity createPipe(byte mask, int ipos, int jpos, boolean withinGrid) {
		Entity pipe = new Entity();

		PipeTileComponent pipeTileComp = new PipeTileComponent();
		pipeTileComp.mask = mask;

		TextureComponent textureComp = new TextureComponent();
		textureComp.region = PipeGameArt.pipeRegions.get(mask).region;

		TransformComponent transComp = new TransformComponent();
		float pipeWidth = textureComp.size.x;
		float pipeHeight = textureComp.size.y;
		float gridOffsetX = -GRID_LENGTH * pipeWidth / 2f;
		float gridOffsetY = -GRID_LENGTH * pipeHeight / 2f;
		transComp.position.set(gridOffsetX + 0.5f * (2 * ipos + 1) * pipeWidth, gridOffsetY + 0.5f * (2 * jpos + 1) * pipeHeight, 0);
		
		transComp.rotation = PipeGameArt.pipeRegions.get(mask).rotation;
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		
		ClickComponent clickComp = new ClickComponent();
		clickComp.active = withinGrid;
		clickComp.clicker = new ClickInterface() {
			@Override
			public void onClick(Entity entity) {
				TransformComponent tc = ComponentMappers.transform.get(entity);
				tc.rotation -= 90f;
				if (tc.rotation > 360f)
					tc.rotation += 360f;
				PipeTileComponent ptc = ComponentMappers.pipetile.get(entity);
				ptc.mask = rotateMask(ptc.mask);
			}
		};
		clickComp.shape = new Rectangle().setSize(textureComp.size.x, textureComp.size.y).setCenter(0f, 0f);
		
		pipe.add(pipeTileComp).add(textureComp).add(transComp).add(clickComp);

		return pipe;
	}
	
	public Entity createFluid(Entity pipe, int entryDirection) {
		Entity entity = new Entity();
		
		PipeTileComponent pipeTileComp = ComponentMappers.pipetile.get(pipe);
		entity.add(pipeTileComp);
		
		TransformComponent pipeTransComp = ComponentMappers.transform.get(pipe);
		entity.add(pipeTransComp);
		
		PipeFluidComponent pipeFluidComp = new PipeFluidComponent();
		pipeFluidComp.filling = true;
		pipeFluidComp.fillDuration = 4f;
		int exitDirection = exitFromEntryDirection(pipeTileComp.mask, entryDirection);
		pipeFluidComp.exitMask = maskFromDirection(exitDirection);
		pipeFluidComp.parentPipe = pipe;
		entity.add(pipeFluidComp);
		
		RotatedAnimationData animData = PipeGameArt.fluidRegions.get(pipeTileComp.mask).get(entryDirection);
		AnimationComponent animComp = new AnimationComponent();
		animComp.animations.put(PipeFluidComponent.STATE_FILLING, new Animation(pipeFluidComp.fillDuration/animData.regions.size, animData.regions));
		entity.add(animComp);

		entity.add(Shaders.generateFBOItemComponent("fluid-fb"));
		
		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("fluid");
		entity.add(shaderComp);
		
		ShaderTimeComponent shaderTimeComp = new ShaderTimeComponent();
		entity.add(shaderTimeComp);
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.color.set(0.2f, 0.2f, 1.0f, 1f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		transComp.position.set(pipeTransComp.position.x, pipeTransComp.position.y, -1);
		transComp.rotation = animData.rotation;
		entity.add(transComp);
		
		StateComponent stateComp = new StateComponent();
		stateComp.set(PipeFluidComponent.STATE_FILLING);
		entity.add(stateComp);
		
		return entity;
	}
	
	private Entity createCircuitBorder(int ipos, int jpos, boolean isCorner) {
		Entity entity = new Entity();
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.region = isCorner ? PipeGameArt.circuitCorner : PipeGameArt.circuitSide[SpacePanic.rng.nextInt(PipeGameArt.circuitSide.length)];
		textureComp.color.set(0.8f, 0.8f, 1.0f, 1f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		float gridOffsetX = - GRID_LENGTH / 2f;
		float gridOffsetY = - GRID_LENGTH / 2f;
		transComp.position.set(gridOffsetX + 0.5f * (2 * ipos + 1), gridOffsetY + 0.5f * (2 * jpos + 1), -2);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("light");
		entity.add(shaderComp);
		
		return entity;
	}
	
	private Entity createPipeBG(int ipos, int jpos) {
		byte mask = 0;
		if (ipos > 0)
			mask = connectAtIndex(mask, 3);
		if (ipos < GRID_LENGTH - 1)
			mask = connectAtIndex(mask, 1);
		if (jpos > 0)
			mask = connectAtIndex(mask, 2);
		if (jpos < GRID_LENGTH - 1)
			mask = connectAtIndex(mask, 0);
		
		Entity entity = new Entity();
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.region = PipeGameArt.pipeBGs.get(mask);
		textureComp.color.set(0.6f, 0.6f, 0.6f, 1f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		float gridOffsetX = - GRID_LENGTH / 2f;
		float gridOffsetY = - GRID_LENGTH / 2f;
		transComp.position.set(gridOffsetX + 0.5f * (2 * ipos + 1), gridOffsetY + 0.5f * (2 * jpos + 1), -2);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("light");
		entity.add(shaderComp);
		
		return entity;
	}
	
	private Entity createBackPanel() {
		Entity entity = new Entity();
		
		TextureComponent textureComp = new TextureComponent();
		textureComp.region = PipeGameArt.whitePixel;
		textureComp.size.set(GRID_LENGTH + 2, GRID_LENGTH + 2);
		textureComp.color.set(0.4f, 0.4f, 0.4f, 1f);
		entity.add(textureComp);
		
		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0, 0, -3);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);
		
		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("light");
		entity.add(shaderComp);
		
		return entity;
	}
	
	private Entity createTimer(int duration) {
		Entity entity = new Entity();
		
		PipeComponent pipeComp = new PipeComponent();
		entity.add(pipeComp);

		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "retro";
		fontComp.string = createTimerString(duration);
		fontComp.color.set(Color.WHITE);
		fontComp.centering = true;
		entity.add(fontComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0, GRID_LENGTH / 2f + 0.5f, 0);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);
		
		TickerComponent tickComp = new TickerComponent();
		tickComp.duration = duration;
		tickComp.interval = 1;
		tickComp.ticker = new EventInterface() {
			@Override
			public void dispatchEvent(Entity entity) {
				BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(entity);
				TickerComponent tc = ComponentMappers.ticker.get(entity);
				bfc.string = createTimerString((int)(tc.totalTimeLeft));
			}
		};
		tickComp.start();
		entity.add(tickComp);

		return entity;
	}
	
	private Entity createFluidFBO() {
		Entity entity = new Entity();
		
		TextureComponent texComp = new TextureComponent();
		texComp.region = new TextureRegion();
		texComp.size.set(BasicScreen.WORLD_WIDTH, BasicScreen.WORLD_HEIGHT);
		texComp.color.a = 0.8f;
		entity.add(texComp);
		
		FBO_Component fboComp = Shaders.generateFBOComponent("fluid-fb", texComp);
		entity.add(fboComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0f, 0f, -1f);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);
		
		return entity;
	}
	
	static public int numberConnections(byte mask) {
		return ((mask) & 1) + ((mask >> 1) & 1) + ((mask >> 2) & 1) + ((mask >> 3) & 1);
	}

	static public boolean connectedLeft(byte mask) {
		return ((mask >> 3) & 1) == 1;
	}

	static public boolean connectedRight(byte mask) {
		return ((mask >> 1) & 1) == 1;
	}

	static public boolean connectedUp(byte mask) {
		return ((mask) & 1) == 1;
	}

	static public boolean connectedDown(byte mask) {
		return ((mask >> 2) & 1) == 1;
	}
	
	static public boolean connectedAtIndex(byte mask, int index) {
		return ((mask >> index) & 1) == 1;
	}
	
	static public byte connectAtIndex(byte mask, int index) {
		return (byte)(mask | (1 << index));
	}

	static public byte disconnectAtIndex(byte mask, int index) {
		return (byte)(mask & ~(1 << index));
	}
	
	static public int oppositeDirectionIndex(int index) {
		return (index + 2)%4;
	}
	
	static public boolean withinBounds(int i, int j) {
		return !(i < 0 || i >= GRID_LENGTH || j < 0 || j >= GRID_LENGTH);
	}
	
	static public int directionFromMask(byte mask) {
		for (int i = 0; i < 4; ++i) {
			if (connectedAtIndex(mask, i))
				return i;
		}
		return -1;
	}
	
	static public byte maskFromDirection(int dir) {
		return (byte)(1 << dir);
	}
	
	static public byte rotateMask(byte mask) {
		byte ND = (byte) (mask << 1);
		ND = (byte) (ND + ((mask >> 3) & 1));

		return (byte)(ND%16);
	}
	
	static public byte rotateMaskN(byte mask, int N) {
		byte result = mask;
		for (int i = 0; i < N; ++i)
			result = rotateMask(result);
		
		return result;
	}
	
	static public int exitFromEntryDirection(byte mask, int entryDir) {
		int oppositeDir = oppositeDirectionIndex(entryDir);
		if (connectedAtIndex(mask, oppositeDir))
			return oppositeDir;
		
		int dir = (entryDir+1)%4;
		if (connectedAtIndex(mask, dir))
			return dir;
		
		dir = (entryDir+3)%4;
		if (connectedAtIndex(mask, dir))
			return dir;
		
		return oppositeDir;
	}
	
	static public void rotateTile(Entity tile) {
		PipeTileComponent pipeTileComp = ComponentMappers.pipetile.get(tile);
		TransformComponent transComp = ComponentMappers.transform.get(tile);
		
		transComp.rotation -= 90f;
		if (transComp.rotation > 360f)
			transComp.rotation += 360f;
		pipeTileComp.mask = rotateMask(pipeTileComp.mask);
	}
	
	static public String createTimerString(int t) {
		int nminutes = (int)(t/60f);
		int nseconds = (int)(t - nminutes*60);
		
		String timestring = "";
		if (nminutes < 10)
			timestring += "0";
		timestring += Integer.toString(nminutes);
		
		timestring += ":";
		
		if (nseconds < 10)
			timestring += "0";
		timestring += Integer.toString(nseconds);

		return timestring;
	}
	
	static private Array<GridPoint2> createGridDeltas() {
		Array<GridPoint2> deltas = new Array<GridPoint2>();
		deltas.add(new GridPoint2(0, 1));
		deltas.add(new GridPoint2(1, 0));
		deltas.add(new GridPoint2(0, -1));
		deltas.add(new GridPoint2(-1, 0));
		
		return deltas;
	}
}
