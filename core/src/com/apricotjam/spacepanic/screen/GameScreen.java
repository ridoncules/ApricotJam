package com.apricotjam.spacepanic.screen;

import com.apricotjam.spacepanic.GameParameters;
import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.art.MiscArt;
import com.apricotjam.spacepanic.components.*;
import com.apricotjam.spacepanic.components.helmet.HelmetScreenComponent;
import com.apricotjam.spacepanic.components.map.MapScreenComponent;
import com.apricotjam.spacepanic.components.pipe.PipeScreenComponent;
import com.apricotjam.spacepanic.gameelements.GameStats;
import com.apricotjam.spacepanic.gameelements.Resource;
import com.apricotjam.spacepanic.interfaces.TweenInterface;
import com.apricotjam.spacepanic.systems.*;
import com.apricotjam.spacepanic.systems.helmet.HelmetSystem;
import com.apricotjam.spacepanic.systems.map.MapSystem;
import com.apricotjam.spacepanic.systems.pipes.PipeSystem;
import com.apricotjam.spacepanic.systems.pipes.PuzzleDifficulty;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

import java.util.Random;

import shaders.DiffuseShader;

public class GameScreen extends BasicScreen {

	public enum GameState {
		MAZING, PIPING, TRANSITIONING, GAMEOVER
	}

	public static float BACKGROUND_MOVEMENT_FACTOR = 0.1f;
	public static float MAP_X = BasicScreen.WORLD_WIDTH / 2.0f;
	public static float MAP_Y_ON = BasicScreen.WORLD_HEIGHT / 2.0f + 0.3f;
	public static float MAP_Y_OFF = -2.0f;
	public static float PIPE_X = BasicScreen.WORLD_WIDTH / 2.0f;
	public static float PIPE_Y = BasicScreen.WORLD_HEIGHT / 2.0f + 0.3f;
	public static float PIPE_SCALE_SMALL = 0.1f;
	public static float PIPE_SCALE_LARGE = 1.0f;

	private GameState currentState;

	private Entity mapSystemEntity;
	private Entity helmetSystemEntity;
	private Entity pipeSystemEntity;
	private Entity backgroundEntity;
	private TextureComponent backgroundTexComp;

	private Random rng = new Random();

	private PipeSystem pipeSystem = null;
	
	private float currDifficulty = 0;

	private boolean badPipes = false;
	private boolean dying = false;
	private float dyingTime = GameParameters.DEATH_TIME;
	private int dyingState = (int)GameParameters.DEATH_TIME;

	private GameStats gameStats = new GameStats();

	public GameScreen(SpacePanic spacePanic) {
		super(spacePanic);

		add(new ClickSystem());
		add(new TweenSystem());
		add(new MovementSystem());
		add(new ScrollSystem());
		add(new LineSystem());
		add(new TickerSystem());
		add(new AnimationSystem());
		add(new AnimatedShaderSystem());
		add(new ShaderLightingSystem());

		addHelmetSystem();
		addMapSystem();

		backgroundEntity = createBackground();
		setBackgroundPosition();
		add(backgroundEntity);

		currentState = GameState.MAZING;
	}

	@Override
	public void render(float delta) {
		super.render(delta);

		switch (currentState) {
			case MAZING:
				updateMaze();
				break;
			case PIPING:
				updatePipe();
				break;
		}

		if (currentState != GameState.GAMEOVER) {
			for (Resource r: Resource.values()) {
				alterResource(r, GameParameters.RESOURCE_DEPLETION.get(r) * delta);
			}
	
			if (dying) {
				updateDying(delta);
			} else {
				gameStats.timeAlive += delta;
			}
		}
	}

	private void updateMaze() {
		MapScreenComponent msc = ComponentMappers.mapscreen.get(mapSystemEntity);
		if (msc.currentState == MapScreenComponent.State.ENCOUNTER) {
			resourceEncountered(msc.encounterResource);
		} else if (msc.currentState == MapScreenComponent.State.EXPLORING) {
			setBackgroundPosition();
		}
	}

	private void updatePipe() {
		PipeScreenComponent psc = ComponentMappers.pipescreen.get(pipeSystemEntity);
		if (psc.currentState == PipeScreenComponent.State.SUCCESS) {
			pipeGameComplete(psc.resource, true);
		} else if (psc.currentState == PipeScreenComponent.State.FAIL) {
			pipeGameComplete(psc.resource, false);
		}
	}

	private void updateDying(float delta) {
		dyingTime -= delta;
		if (dyingTime < dyingState) {
			if (dyingState == 0) {
				gameOver();
			} else {
				if (dyingState <= 5) {
					addMessage(Integer.toString(dyingState), Color.RED, 1.0f, false, true);
				}
				dyingState--;
			}
		}
	}

	private void resourceEncountered(Resource resource) {
		MapScreenComponent msc = ComponentMappers.mapscreen.get(mapSystemEntity);
		msc.viewSize++;
		msc.currentState = MapScreenComponent.State.PAUSED;
		ComponentMappers.tween.get(mapSystemEntity).tweenSpecs.add(mapOutTween());
		currentState = GameState.TRANSITIONING;

		addPipeSystem(resource);
		ComponentMappers.tween.get(pipeSystemEntity).tweenSpecs.add(pipeInTween());
	}

	private void pipeGameComplete(Resource resource, boolean success) {
		ComponentMappers.tween.get(pipeSystemEntity).tweenSpecs.add(pipeOutTween());
		PipeScreenComponent psc = ComponentMappers.pipescreen.get(pipeSystemEntity);
		psc.currentState = PipeScreenComponent.State.PAUSED;
		currentState = GameState.TRANSITIONING;

		ComponentMappers.tween.get(mapSystemEntity).tweenSpecs.add(mapInTween());

		if (success) {
			addMessage(resource.name().replace("_", " ") + " acquired", Color.GREEN, 3.0f, true, false);
			gameStats.addResource(resource);
			gameStats.difficulty += GameParameters.PUZZLE_DIFFICULTY_INC;
			
			currDifficulty += GameParameters.PUZZLE_DIFFICULTY_INC;
			while (currDifficulty >= PuzzleDifficulty.gridSize.size)
				currDifficulty -= PuzzleDifficulty.gridSize.size;
				
			if (badPipes) {
				alterResource(resource, GameParameters.RESOURCE_GAIN_ALT.get(resource));
			} else {
				alterResource(resource, GameParameters.RESOURCE_GAIN.get(resource));
			}
		} else {
			addMessage(resource + " lost", Color.RED, 3.0f, true, false);
		}
	}

	private void alterResource(Resource resource, float amount) {
		HelmetScreenComponent hsc = ComponentMappers.helmetscreen.get(helmetSystemEntity);
		float old = hsc.resourceCount.get(resource);
		float max = hsc.maxCount.get(resource);
		float next = MathUtils.clamp(old + amount, 0.0f, max);
		hsc.resourceCount.put(resource, next);

		switch (resource) {
			case OXYGEN:
				if (!dying && next == 0.0f) {
					dying = true;
					addMessage("Oxygen depleted, you will die in...", Color.RED, 4.0f, true, false);
				} else if (dying && next > 0.0f) {
					dying = false;
					dyingTime = GameParameters.DEATH_TIME;
					dyingState = (int)GameParameters.DEATH_TIME;
				}
				break;
			case DEMISTER:
				updateFog(next / max);
				break;
			case PIPE_CLEANER:
				if (!badPipes && next == 0.0f) {
					badPipes = true;
					addMessage("Pipe cleaner exhausted, resource gain reduced", Color.RED, 4.0f, true, false);
				} else if (badPipes && next > 0.0f) {
					badPipes = false;
					addMessage("Resource gain normal", Color.GREEN, 3.0f, true, false);
				}
				break;
			case PLUTONIUM:
				updateViewSize(next / max);
				break;
		}
	}

	private void addMessage(String text, Color color, float time, boolean scroll, boolean flash) {
		HelmetScreenComponent hsc = ComponentMappers.helmetscreen.get(helmetSystemEntity);
		hsc.messages.addLast(new HelmetSystem.LED_Message(text, color, time, scroll, flash));
	}

	private void gameOver() {
		if (currentState != GameState.GAMEOVER && currentState != GameState.TRANSITIONING) {
			addMessage("GAME OVER", Color.RED, 3600.0f, false, true);
			ComponentMappers.mapscreen.get(mapSystemEntity).currentState = MapScreenComponent.State.PAUSED;
			if (pipeSystem != null) {
				pipeSystem.setProcessing(false);
			}
			
			// Start zooming into outer space for the next screen.
			// TODO: the stencil location on screen for LED won't scale properly.
			// TODO: might want to move stars too.
			
			float duration = 2f;
			float helmetDelay = 0.5f;
			
			if (currentState == GameState.PIPING) {
				PipeScreenComponent pipeScreenComp = ComponentMappers.pipescreen.get(pipeSystemEntity);
				TweenComponent pipeTweenComp = ComponentMappers.tween.get(pipeSystemEntity);
				float currPipeY = ComponentMappers.transform.get(pipeSystemEntity).position.y;
				pipeTweenComp.tweenSpecs.add(pipeGoneTween(duration));
			}
			
			MapScreenComponent mapScreenComp = ComponentMappers.mapscreen.get(mapSystemEntity);
			TweenComponent mapTweenComp = ComponentMappers.tween.get(mapSystemEntity);
			float currMapY = ComponentMappers.transform.get(mapSystemEntity).position.y;
			mapTweenComp.tweenSpecs.add(mapGoneTween(duration));
			
			float helmetTweenDuration = 3f;
			HelmetScreenComponent helmetScreenComp = ComponentMappers.helmetscreen.get(helmetSystemEntity);
			TweenComponent helmetTweenComp = new TweenComponent();
			helmetTweenComp.tweenSpecs.add(helmetGoneTween(helmetDelay, helmetTweenDuration));
			helmetTweenComp.tweenSpecs.add(fogGoneTween(helmetScreenComp.demisterSpread, duration));
			helmetSystemEntity.add(helmetTweenComp);
			
			add(createEndEntity(helmetDelay + helmetTweenDuration));
			
			System.out.println("You survived for " + gameStats.timeAlive + " seconds");
			
			currentState = GameState.GAMEOVER;
		}
	}

	private void updateFog(float fraction) {
		HelmetScreenComponent hsc = ComponentMappers.helmetscreen.get(helmetSystemEntity);
		hsc.demisterSpread = GameParameters.FOG_MIN + (fraction * fraction) * (GameParameters.FOG_MAX - GameParameters.FOG_MIN);
	}

	private void updateViewSize(float fraction) {
		MapScreenComponent msc = ComponentMappers.mapscreen.get(mapSystemEntity);
		msc.viewSize = GameParameters.MIN_VIEWSIZE + fraction * (GameParameters.MAX_VIEWSIZE - GameParameters.MIN_VIEWSIZE);
	}

	private TweenSpec mapOutTween() {
		TweenSpec ts = new TweenSpec();
		ts.start = MAP_Y_ON;
		ts.end =  MAP_Y_OFF;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = 2.0f;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).position.y = a;
			}
		};
		return ts;
	}

	private TweenSpec mapInTween() {
		TweenSpec ts = new TweenSpec();
		ts.start = MAP_Y_OFF;
		ts.end =  MAP_Y_ON;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = 2.0f;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).position.y = a;
			}

			@Override
			public void endTween(Entity e) {
				currentState = GameState.MAZING;
				ComponentMappers.mapscreen.get(e).currentState = MapScreenComponent.State.EXPLORING;
			}
		};
		return ts;
	}

	private TweenSpec pipeOutTween() {
		TweenSpec ts = new TweenSpec();
		ts.start = PIPE_X;
		ts.end = PIPE_X + BasicScreen.WORLD_WIDTH;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = 2.0f;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).position.x = a;
			}

			@Override
			public void endTween(Entity e) {
				engine.removeSystem(pipeSystem);
				engine.removeEntity(pipeSystemEntity);
			}
		};
		return ts;
	}

	private TweenSpec pipeInTween() {
		TweenSpec ts = new TweenSpec();
		ts.start = PIPE_SCALE_SMALL;
		ts.end =  PIPE_SCALE_LARGE;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = 2.0f;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).scale.x = a;
				ComponentMappers.transform.get(e).scale.y = a;
			}

			@Override
			public void endTween(Entity e) {
				currentState = GameState.PIPING;
				pipeSystem.start();
			}
		};
		return ts;
	}
	
	private TweenSpec mapGoneTween(float duration) {
		float currMapY = ComponentMappers.transform.get(mapSystemEntity).position.y;
		
		TweenSpec ts = new TweenSpec();
		ts.start = currMapY;
		ts.end =  2f*MAP_Y_OFF;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = duration;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).position.y = a;
			}
		};
		return ts;
	}
	
	private TweenSpec pipeGoneTween(float duration) {
		TweenSpec ts = new TweenSpec();
		ts.start = PIPE_X;
		ts.end = PIPE_X + BasicScreen.WORLD_WIDTH;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = duration;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).position.x = a;
			}

			@Override
			public void endTween(Entity e) {
				engine.removeSystem(pipeSystem);
				engine.removeEntity(pipeSystemEntity);
			}
		};
		return ts;
	}
	
	private TweenSpec helmetGoneTween(float delay, float helmetGoneDuration) {
		final float origStart = 1f;
		float origEnd = 2f;
		float speed = (origEnd - origStart)/helmetGoneDuration;
		float start = origStart - speed*delay;
		
		TweenSpec ts = new TweenSpec();
		ts.start = start;
		ts.end =  origEnd;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.linear;
		ts.period = helmetGoneDuration + delay;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				TransformComponent tc = ComponentMappers.transform.get(e);
				tc.scale.x = Math.max(a, origStart);
				tc.scale.y = Math.max(a, origStart);
			}
		};
		
		return ts;
	}
	
	private TweenSpec fogGoneTween(float currDemisterSpread, float duration) {
		TweenSpec ts = new TweenSpec();
		ts.start = currDemisterSpread;
		ts.end = 10*GameParameters.FOG_MAX;
		ts.period = duration;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.helmetscreen.get(e).demisterSpread = a;
			}
		};
		
		return ts;
	}
	
	private Entity createEndEntity(float delay) {
		Entity entity = new Entity();
		
		TweenComponent tweenComp = new TweenComponent();
		TweenSpec ts = new TweenSpec();
		ts.period = delay;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void endTween(Entity e) {
				Entity nextBackgroundEntity = createBackground();
				ComponentMappers.texture.get(nextBackgroundEntity).region = ComponentMappers.texture.get(backgroundEntity).region;
				spacePanic.setScreen(new GameOverScreen(spacePanic, nextBackgroundEntity, gameStats));
			}

			@Override
			public void applyTween(Entity e, float a) {
			}
		};
		tweenComp.tweenSpecs.add(ts);
		entity.add(tweenComp);
		
		return entity;
	}

	private void setBackgroundPosition() {
		MapScreenComponent msc = ComponentMappers.mapscreen.get(mapSystemEntity);
		float width = backgroundTexComp.region.getRegionWidth();
		float height = backgroundTexComp.region.getRegionHeight();
		float x = msc.playerPosition.x * BACKGROUND_MOVEMENT_FACTOR * width / backgroundTexComp.size.x;
		float y = msc.playerPosition.y * BACKGROUND_MOVEMENT_FACTOR * height / backgroundTexComp.size.y;
		backgroundTexComp.region.setRegionX((int)(x));
		backgroundTexComp.region.setRegionWidth((int)width);
		backgroundTexComp.region.setRegionY(-1 * (int)(y));
		backgroundTexComp.region.setRegionHeight((int)height);
	}

	private void addMapSystem() {
		mapSystemEntity = new Entity();

		MapScreenComponent msc = new MapScreenComponent();
		msc.viewSize = GameParameters.MAX_VIEWSIZE;
		mapSystemEntity.add(msc);

		TransformComponent tranc = new TransformComponent();
		tranc.position.x = MAP_X;
		tranc.position.y = BasicScreen.WORLD_HEIGHT / 2.0f + 0.3f;
		tranc.position.z = 10.0f;
		mapSystemEntity.add(tranc);

		mapSystemEntity.add(new TweenComponent());

		add(mapSystemEntity);
		add(new MapSystem(mapSystemEntity, 8.25f, 4.75f));
	}

	private void addHelmetSystem() {
		helmetSystemEntity = new Entity();

		HelmetScreenComponent helmetScreenComponent = new HelmetScreenComponent();
		for (Resource r: Resource.values()) {
			helmetScreenComponent.maxCount.put(r, GameParameters.RESOURCE_MAX.get(r));
			helmetScreenComponent.resourceCount.put(r, GameParameters.RESOURCE_MAX.get(r));
		}
		helmetScreenComponent.demisterSpread = 3.0f;
		helmetSystemEntity.add(helmetScreenComponent);
		
		TransformComponent transComp = new TransformComponent();
		transComp.position.x = BasicScreen.WORLD_WIDTH/2f;
		transComp.position.y = BasicScreen.WORLD_HEIGHT/2f;
		transComp.position.z = 20.0f;
		transComp.scale.x = 1f;
		transComp.scale.y = 1f;
		helmetSystemEntity.add(transComp);

		add(helmetSystemEntity);
		add(new HelmetSystem(helmetSystemEntity));
	}

	private void addPipeSystem(Resource resource) {
		pipeSystemEntity = new Entity();

		PipeScreenComponent pipeScreenComp = new PipeScreenComponent();
		pipeScreenComp.currentState = PipeScreenComponent.State.PAUSED;
		pipeScreenComp.resource = resource;
		pipeSystemEntity.add(pipeScreenComp);

		TransformComponent tranc = new TransformComponent();
		tranc.position.x = PIPE_X;
		tranc.position.y = PIPE_Y;
		tranc.position.z = 5.0f;
		tranc.scale.x = PIPE_SCALE_SMALL;
		tranc.scale.y = PIPE_SCALE_SMALL;
		pipeSystemEntity.add(tranc);

		TweenComponent tweenc = new TweenComponent();
		pipeSystemEntity.add(tweenc);

		pipeSystem = new PipeSystem(pipeSystemEntity, getPipeDifficulty(resource));
		add(pipeSystemEntity);
		add(pipeSystem);
	}

	private int getPipeDifficulty(Resource resource) {
		/*
		int diff = GameParameters.RESOURCE_MIN_PIPE_DIFFICULTY.get(resource)
				+ rng.nextInt(GameParameters.RESOURCE_SPREAD_PIPE_DIFFICULTY.get(resource));
		diff = Math.min(diff, 25);
		*/
		
		return Math.min((int)currDifficulty, PuzzleDifficulty.gridSize.size-1);
	}

	private Entity createBackground() {
		Entity e = new Entity();

		backgroundTexComp = new TextureComponent();
		Texture tex = MiscArt.mainBackgroundScrollable;
		float texToCorner = (float)Math.sqrt((tex.getWidth() * tex.getWidth()) + (tex.getHeight() * tex.getHeight()));
		backgroundTexComp.region = new TextureRegion(tex, 0, 0, (int)texToCorner, (int)texToCorner);

		backgroundTexComp.size.x = texToCorner * RenderingSystem.PIXELS_TO_WORLD;
		backgroundTexComp.size.y = texToCorner * RenderingSystem.PIXELS_TO_WORLD;

		TransformComponent transComp = new TransformComponent();
		transComp.position.x = BasicScreen.WORLD_WIDTH / 2.0f;
		transComp.position.y = BasicScreen.WORLD_HEIGHT / 2.0f;
		transComp.position.z = -1000.0f;

		e.add(backgroundTexComp);
		e.add(transComp);

		return e;
	}

	@Override
	public void backPressed() {
		spacePanic.setScreen(new MenuScreen(spacePanic));
	}
}
