package com.apricotjam.spacepanic.systems.helmet;

import com.apricotjam.spacepanic.GameParameters;
import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.art.*;
import com.apricotjam.spacepanic.art.PipeGameArt.RotatedAnimationData;
import com.apricotjam.spacepanic.art.PipeGameArt.RotatedRegionData;
import com.apricotjam.spacepanic.components.*;
import com.apricotjam.spacepanic.components.helmet.HelmetPartComponent;
import com.apricotjam.spacepanic.components.helmet.LED_Component;
import com.apricotjam.spacepanic.components.helmet.ResourcePipeComponent;
import com.apricotjam.spacepanic.gameelements.Resource;
import com.apricotjam.spacepanic.interfaces.ClickInterface;
import com.apricotjam.spacepanic.interfaces.TweenInterface;
import com.apricotjam.spacepanic.screen.BasicScreen;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Interpolation;

public class HelmetWorld {
	public static float HELMET_Z = 20f;
	public static float LEDBG_X = (71f / 80f) * BasicScreen.WORLD_WIDTH - 0.5f * BasicScreen.WORLD_WIDTH;
	public static float LEDBG_Y = (7f / 80f) * BasicScreen.WORLD_HEIGHT - 0.5f * BasicScreen.WORLD_HEIGHT;
	public static float LEDBG_W = 3f;
	public static float LEDBG_H = 0.7f;

	private static final float[] PIPE_LENGTHS = {2.8f, 2.25f, 1.7f, 1.15f};
	private static final float PIPE_HEIGHT = 0.6f;
	private static final float PIPE_SPACING = 0.42f;

	private Entity masterEntity;

	private TransformComponent resourcePanelTransform;
	private Entity demisterFog;

	public HelmetWorld(Entity masterEntity) {
		this.masterEntity = masterEntity;
	}

	public void build(Engine engine) {

		// Create FBOs.
		//engine.addEntity(createLED_FBO2());
		//engine.addEntity(createLED_FBO1());

		// Create helmet.
		engine.addEntity(createHelmet());

		// Create helmet features.
		//// Panels.
		engine.addEntity(createSidePanel(true));
		engine.addEntity(createSidePanel(false));
		Entity resourcePanel = createResourcePanel();
		resourcePanelTransform = ComponentMappers.transform.get(resourcePanel);
		engine.addEntity(resourcePanel);
		engine.addEntity(createLED_Frame());

		//// Screws.
		engine.addEntity(createScrew((46f / 1280f - 0.5f) * BasicScreen.WORLD_WIDTH, (240f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT));
		engine.addEntity(createScrew((48f / 1280f - 0.5f) * BasicScreen.WORLD_WIDTH, (560f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT, true));
		engine.addEntity(createScrew((439f / 1280f - 0.5f) * BasicScreen.WORLD_WIDTH, (699f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT));
		engine.addEntity(createScrew((0.5f - 46f / 1280f) * BasicScreen.WORLD_WIDTH, (240f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT));
		engine.addEntity(createScrew((0.5f - 48f / 1280f) * BasicScreen.WORLD_WIDTH, (560f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT));
		engine.addEntity(createScrew((0.5f - 439f / 1280f) * BasicScreen.WORLD_WIDTH, (699f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT));

		//// Speaker.
		engine.addEntity(createSpeaker());

		float bottomRightPipeX = -0.08f * BasicScreen.WORLD_WIDTH;
		float bottomRightPipeY = -0.105f * BasicScreen.WORLD_HEIGHT;

		for (int i = 0; i < 4; i++) {
			// Pipe fluid
			engine.addEntity(createFluidLine(Resource.values()[i], PIPE_LENGTHS[i], bottomRightPipeX, bottomRightPipeY + i * PIPE_SPACING));

			// Pipe outlines.
			engine.addEntity(createPipeLine(PIPE_LENGTHS[i], bottomRightPipeX, bottomRightPipeY + i * PIPE_SPACING));

			//// Pipe caps.
			engine.addEntity(createPipeCap(bottomRightPipeX - (1.0f / 4.0f) * PIPE_SPACING, bottomRightPipeY + (i + 0.725f) * PIPE_SPACING, true));
			engine.addEntity(createPipeCap(bottomRightPipeX + PIPE_LENGTHS[i] + (1.0f / 4.0f) * PIPE_SPACING, bottomRightPipeY + (i + 0.725f) * PIPE_SPACING, false));

			//// Resource icons.
			engine.addEntity(createResourceIcon(bottomRightPipeX - 0.75f * PIPE_SPACING, bottomRightPipeY + (i + 0.725f) * PIPE_SPACING, i));
		}

		//// Fog.
		engine.addEntity(createFog());
		demisterFog = createDemisterFog();
		engine.addEntity(demisterFog);

		// Create black marquee.
		//engine.addEntity(createLED_PanelShadow());
		engine.addEntity(createLED_Panel());
	}

	public Entity getDemisterFog() {
		return demisterFog;
	}

	private Entity createHelmet() {
		Entity e = new Entity();
		e.add(new HelmetPartComponent());

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.base;
		texComp.size.x = BasicScreen.WORLD_WIDTH;
		texComp.size.y = BasicScreen.WORLD_HEIGHT;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0f, 0f, 0f);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		return e;
	}

	private Entity createSpeaker() {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.speaker;
		texComp.size.x = 2f;
		texComp.size.y = 2f;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0, -0.56f * BasicScreen.WORLD_HEIGHT, 1);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		return e;
	}

	private Entity createScrew(float x, float y) {
		return createScrew(x, y, false);
	}

	private Entity createScrew(float x, float y, boolean trick) {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.screw;
		texComp.size.x = 0.4f;
		texComp.size.y = 0.4f;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(x, y, 1f);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		if (trick) {
			e.add(new TweenComponent());
			ClickComponent clickComponent = new ClickComponent();
			clickComponent.shape = new Circle(0.0f, 0.0f, 0.4f);
			clickComponent.clicker = new ClickInterface() {
				@Override
				public void onClick(Entity entity) {
					TweenComponent tweenComponent = ComponentMappers.tween.get(entity);
					tweenComponent.tweenSpecs.add(spinTween());
				}
			};
			e.add(clickComponent);
		}

		return e;
	}

	private TweenSpec spinTween() {
		TweenSpec ts = new TweenSpec();
		ts.start = 0.0f;
		ts.end = 360.0f;
		ts.cycle = TweenSpec.Cycle.ONCE;
		ts.interp = Interpolation.exp10;
		ts.period = 2.0f;
		ts.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.transform.get(e).rotation = a;
			}
		};
		return ts;
	}

	private Entity createLED_Frame() {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.ledFrame;
		texComp.size.x = 6.00f;
		texComp.size.y = 2.00f;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X, LEDBG_Y, 5);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		return e;
	}

	private Entity createSidePanel(boolean isLeft) {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = isLeft ? HelmetUI.sidepanelLeft : HelmetUI.sidepanelRight;
		texComp.size.x = texComp.region.getRegionWidth() * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;
		texComp.size.y = texComp.region.getRegionHeight() * BasicScreen.WORLD_HEIGHT / SpacePanic.HEIGHT;
		texComp.color.set(0.75f, 0.75f, 0.75f, 1f);
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.x = isLeft ? texComp.size.x / 2f - 0.5f * BasicScreen.WORLD_WIDTH : 0.5f * BasicScreen.WORLD_WIDTH - texComp.size.x / 2f;
		transComp.position.y = texComp.size.y / 2f - 0.5f * BasicScreen.WORLD_HEIGHT;
		transComp.position.z = 1;
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		return e;
	}

	private Entity createResourcePanel() {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.resourcePanel;
		texComp.size.x = texComp.region.getRegionWidth() * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;
		texComp.size.y = texComp.region.getRegionHeight() * BasicScreen.WORLD_HEIGHT / SpacePanic.HEIGHT;
		texComp.color.set(0.5f, 0.5f, 0.5f, 1f);
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.x = texComp.size.x / 2f + (16f / 1280f - 0.5f) * BasicScreen.WORLD_WIDTH;
		transComp.position.y = texComp.size.y / 2f + (16f / 720f - 0.5f) * BasicScreen.WORLD_HEIGHT;
		transComp.position.z = 2;
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		e.add(shaderComp);

		return e;
	}

	private Entity createPipeLine(float length, float x, float y) {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		RotatedRegionData rotRegionData = PipeGameArt.pipeRegions.get((byte) (10));
		texComp.region = rotRegionData.region;
		texComp.size.x = length;
		texComp.size.y = PIPE_HEIGHT;
		texComp.centre = false;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(x, y, 2);
		transComp.rotation = rotRegionData.rotation;
		transComp.parent = resourcePanelTransform;
		e.add(transComp);

		return e;
	}

	private Entity createFluidLine(Resource resource, float length, float x, float y) {
		Entity entity = new Entity();

		RotatedAnimationData animData = PipeGameArt.fluidRegions.get((byte) (10)).get(3);
		//AnimationComponent animComp = new AnimationComponent();
		//animComp.animations.put(0, new Animation(1.0f / animData.regions.size, animData.regions));
		//entity.add(animComp);

		TextureComponent texComp = new TextureComponent();
		texComp.size.x = length;
		texComp.size.y = PIPE_HEIGHT;
		texComp.color.set(HelmetUI.resourceColors.get(resource));
		texComp.region = new TextureRegion(animData.regions.get(animData.regions.size - 1));
		texComp.centre = false;
		entity.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(x, y, 1);
		transComp.rotation = animData.rotation;
		transComp.parent = resourcePanelTransform;
		entity.add(transComp);

		ResourcePipeComponent resourcePipeComp = new ResourcePipeComponent();
		resourcePipeComp.resource = resource;
		resourcePipeComp.maxSize = length;
		entity.add(resourcePipeComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("fluid");
		entity.add(shaderComp);

		ShaderTimeComponent shaderTimeComp = new ShaderTimeComponent();
		entity.add(shaderTimeComp);

		return entity;
	}

	private Entity createPipeCap(float x, float y, boolean isLeft) {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = isLeft ? PipeGameArt.pipeCapLeft : PipeGameArt.pipeCapRight;
		texComp.size.x = PIPE_SPACING;
		texComp.size.y = PIPE_SPACING;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(x, y, 3);
		transComp.position.y = y;
		transComp.position.z = 3;
		transComp.parent = resourcePanelTransform;
		e.add(transComp);

		return e;
	}

	private Entity createResourceIcon(float x, float y, int index) {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = MapArt.resourceIcons.get(index);
		texComp.size.x = 0.9f * PIPE_SPACING;
		texComp.size.y = 0.9f * PIPE_SPACING;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(x, y, 4);
		transComp.parent = resourcePanelTransform;
		e.add(transComp);

		return e;
	}

	private Entity createFog() {
		Entity e = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.fog;
		texComp.size.x = BasicScreen.WORLD_WIDTH;
		texComp.size.y = BasicScreen.WORLD_HEIGHT;
		e.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0f, 0f, -1f);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		e.add(transComp);

		TweenComponent tweenComp = new TweenComponent();

		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0.0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 2.0f;
		tweenSpec.cycle = TweenSpec.Cycle.INFLOOP;
		tweenSpec.reverse = true;
		tweenSpec.interp = Interpolation.fade;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				TextureComponent tc = ComponentMappers.texture.get(e);
				tc.color.a = 1.0f - a;
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		e.add(tweenComp);

		return e;
	}

	public Entity createDemisterFog() {
		Entity entity = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = HelmetUI.fog2;
		texComp.size.x = BasicScreen.WORLD_WIDTH;
		texComp.size.y = BasicScreen.WORLD_HEIGHT;
		texComp.color.a = 0f;
		entity.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(0f, 0f, -1f);
		transComp.position.x = BasicScreen.WORLD_WIDTH / 2.0f;
		transComp.position.y = BasicScreen.WORLD_HEIGHT / 2.0f;
		transComp.position.z = HELMET_Z - 1;
		entity.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("fog");
		entity.add(shaderComp);

		ShaderSpreadComponent shaderSpreadComp = new ShaderSpreadComponent();
		shaderSpreadComp.spread = GameParameters.FOG_MAX;
		entity.add(shaderSpreadComp);
		
		TweenComponent tweenComp = new TweenComponent();
		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.period = 10f;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				ComponentMappers.texture.get(e).color.a = a;
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		entity.add(tweenComp);

		return entity;
	}

	private Entity createLED_PanelShadow() {
		float offset = 0.003f * BasicScreen.WORLD_WIDTH;
		TextureComponent texComp = new TextureComponent();
		texComp.region = PipeGameArt.whitePixel;
		texComp.color.set(0.4f, 0.4f, 0.4f, 1.0f);
		texComp.size.x = LEDBG_W + offset;
		texComp.size.y = LEDBG_H + offset;

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X - 0.5f * offset, LEDBG_Y - 0.5f * offset, HELMET_Z + 2);

		Entity entity = new Entity();
		entity.add(texComp).add(transComp);

		return entity;
	}

	private Entity createLED_Panel() {
		Entity entity = new Entity();

		TextureComponent texComp = new TextureComponent();
		texComp.region = PipeGameArt.ledBG;
		texComp.color = new Color(Color.BLACK);
		texComp.size.x = (1.01f) * LEDBG_W;
		texComp.size.y = LEDBG_H;
		entity.add(texComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X, LEDBG_Y, 2);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("helmet-light");
		entity.add(shaderComp);

		return entity;
	}

	private Entity createLED_FBO1() {
		Entity entity = new Entity();

		TextureComponent texc = new TextureComponent();
		texc.size.x = BasicScreen.WORLD_WIDTH;
		texc.size.y = BasicScreen.WORLD_HEIGHT;
		entity.add(texc);

		entity.add(Shaders.generateFBOComponent("led-fb1", texc));
		//entity.add(Shaders.generateFBOItemComponent("led-fb2"));

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("led-blur");
		entity.add(shaderComp);

		ShaderDirectionComponent shaderDirComp = new ShaderDirectionComponent();
		shaderDirComp.direction.set(1f, 0f);
		entity.add(shaderDirComp);

		TransformComponent tranc = new TransformComponent();
		float z = ComponentMappers.transform.get(masterEntity).position.z;
		tranc.position.set(BasicScreen.WORLD_WIDTH / 2f, BasicScreen.WORLD_HEIGHT / 2f, 3f + z);
		//tranc.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(tranc);

		return entity;
	}

	private Entity createLED_FBO2() {
		Entity entity = new Entity();

		TextureComponent texc = new TextureComponent();
		texc.size.x = BasicScreen.WORLD_WIDTH;
		texc.size.y = BasicScreen.WORLD_HEIGHT;
		entity.add(texc);

		entity.add(Shaders.generateFBOComponent("led-fb2", texc));

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("led-blur");
		entity.add(shaderComp);

		ShaderDirectionComponent shaderDirComp = new ShaderDirectionComponent();
		shaderDirComp.direction.set(0f, 1f);
		entity.add(shaderDirComp);

		TransformComponent tranc = new TransformComponent();
		float z = ComponentMappers.transform.get(masterEntity).position.z;
		tranc.position.set(BasicScreen.WORLD_WIDTH / 2f, BasicScreen.WORLD_HEIGHT / 2f, 3f + z);
		//tranc.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(tranc);

		return entity;
	}

	public Entity createMarqueeLED(String text) {
		Entity entity = new Entity();

		LED_Component ledComp = new LED_Component();
		entity.add(ledComp);

		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "led";
		fontComp.centering = true;
		fontComp.string = text;
		fontComp.color.set(1f, 1f, 0f, 1f);
		GlyphLayout layout = new GlyphLayout(); //dont do this every frame! Store it as member
		layout.setText(Assets.fonts.get(fontComp.font), text);
		fontComp.layout = layout;
		entity.add(fontComp);

		entity.add(Shaders.generateFBOItemComponent("led-fb1"));

		float width = layout.width * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;// contains the width of the current set text
		float height = layout.height * BasicScreen.WORLD_HEIGHT / SpacePanic.HEIGHT; // contains the height of the current set text
		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X + (LEDBG_W + width) / 2f, LEDBG_Y, 0);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		TweenComponent tweenComp = new TweenComponent();
		entity.add(tweenComp);

		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0.0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 4f;
		tweenSpec.loops = 2;
		tweenSpec.cycle = TweenSpec.Cycle.LOOP;
		tweenSpec.interp = Interpolation.linear;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				TransformComponent tc = ComponentMappers.transform.get(e);
				BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
				float w = bfc.layout.width * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;
				tc.position.x = LEDBG_X + (LEDBG_W + w) / 2f - a * (LEDBG_W + w);
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		entity.add(tweenComp);

		return entity;
	}

	public Entity createFlashLED(String text) {
		Entity entity = new Entity();

		LED_Component ledComp = new LED_Component();
		entity.add(ledComp);

		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "led";
		fontComp.centering = true;
		fontComp.string = text;
		fontComp.color.set(1f, 0f, 0f, 1f);
		GlyphLayout layout = new GlyphLayout(); //dont do this every frame! Store it as member
		layout.setText(Assets.fonts.get(fontComp.font), text);
		fontComp.layout = layout;
		entity.add(fontComp);

		entity.add(Shaders.generateFBOItemComponent("led-fb1"));

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X, LEDBG_Y, 0);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		TweenComponent tweenComp = new TweenComponent();
		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0.0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 0.8f;
		tweenSpec.cycle = TweenSpec.Cycle.LOOP;
		tweenSpec.loops = 8;
		tweenSpec.reverse = true;
		tweenSpec.interp = Interpolation.sine;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
				bfc.color.a = a;
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		entity.add(tweenComp);

		return entity;
	}

	public Entity createAppearLED(String text) {
		Entity entity = new Entity();

		LED_Component ledComp = new LED_Component();
		entity.add(ledComp);

		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "led";
		fontComp.centering = true;
		fontComp.string = text;
		fontComp.color.set(0f, 1f, 0f, 1f);
		GlyphLayout layout = new GlyphLayout(); //dont do this every frame! Store it as member
		layout.setText(Assets.fonts.get(fontComp.font), text);
		fontComp.layout = layout;
		entity.add(fontComp);

		entity.add(Shaders.generateFBOItemComponent("led-fb1"));

		float width = layout.width * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;// contains the width of the current set text
		float height = layout.height * BasicScreen.WORLD_HEIGHT / SpacePanic.HEIGHT; // contains the height of the current set text
		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X, LEDBG_Y, 0);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		TweenComponent tweenComp = new TweenComponent();
		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0.0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 2f;
		tweenSpec.cycle = TweenSpec.Cycle.ONCE;
		tweenSpec.interp = Interpolation.linear;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
				bfc.color.a = Math.min(2.0f * a, 1);
			}
		};
		tweenComp.tweenSpecs.add(tweenSpec);
		entity.add(tweenComp);

		return entity;
	}

	public Entity createLED(String text, Color color, float time, boolean scroll, boolean flash) {
		Entity entity = new Entity();

		LED_Component ledComp = new LED_Component();
		entity.add(ledComp);

		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "led";
		fontComp.centering = true;
		fontComp.string = text;
		GlyphLayout layout = new GlyphLayout(); //dont do this every frame! Store it as member
		layout.setText(Assets.fonts.get(fontComp.font), text);
		fontComp.layout = layout;
		float width = layout.width * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;// contains the width of the current set text
		fontComp.color.set(color);
		entity.add(fontComp);

		//entity.add(Shaders.generateFBOItemComponent("led-fb1"));

		ShaderComponent shaderComp = new ShaderComponent();
		shaderComp.shader = Shaders.manager.get("mask");
		entity.add(shaderComp);

		ShaderMaskComponent shaderMaskComp = new ShaderMaskComponent();
		shaderMaskComp.position.x = (LEDBG_X - LEDBG_W / 2f);
		shaderMaskComp.position.y = (LEDBG_Y - LEDBG_H / 2f);
		shaderMaskComp.size.x = LEDBG_W;
		shaderMaskComp.size.y = LEDBG_H;
		shaderMaskComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(shaderMaskComp);

		TransformComponent transComp = new TransformComponent();
		transComp.position.set(LEDBG_X, LEDBG_Y, 100f);
		transComp.parent = ComponentMappers.transform.get(masterEntity);
		entity.add(transComp);

		TweenComponent tweenComp = new TweenComponent();
		if (scroll) {
			transComp.position.set(LEDBG_X + (LEDBG_W + width) / 2f, LEDBG_Y, HELMET_Z + 4);
			TweenSpec tweenSpec = new TweenSpec();

			tweenSpec.start = 0.0f;
			tweenSpec.end = 1.0f;
			tweenSpec.period = time;
			tweenSpec.loops = 1;
			tweenSpec.cycle = TweenSpec.Cycle.LOOP;
			tweenSpec.interp = Interpolation.linear;
			tweenSpec.tweenInterface = new TweenInterface() {
				@Override
				public void applyTween(Entity e, float a) {
					TransformComponent tc = ComponentMappers.transform.get(e);
					BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
					float w = bfc.layout.width * BasicScreen.WORLD_WIDTH / SpacePanic.WIDTH;
					tc.position.x = LEDBG_X + (LEDBG_W + w) / 2f - a * (LEDBG_W + w);
				}
			};
			tweenComp.tweenSpecs.add(tweenSpec);
		}
		if (flash) {
			TweenSpec tweenSpec = new TweenSpec();
			tweenSpec.start = 0.0f;
			tweenSpec.end = 1.0f;
			tweenSpec.period = 0.8f;
			tweenSpec.cycle = TweenSpec.Cycle.LOOP;
			tweenSpec.loops = (int) (time / tweenSpec.period);
			tweenSpec.reverse = true;
			tweenSpec.interp = Interpolation.sine;
			tweenSpec.tweenInterface = new TweenInterface() {
				@Override
				public void applyTween(Entity e, float a) {
					BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
					bfc.color.a = a;
				}
			};
			tweenComp.tweenSpecs.add(tweenSpec);
		}

		TweenSpec tweenCompAP = new TweenSpec();
		tweenCompAP.start = 0.0f;
		tweenCompAP.end = 1.0f;
		tweenCompAP.period = 0.8f;
		tweenCompAP.cycle = TweenSpec.Cycle.ONCE;
		tweenCompAP.interp = Interpolation.linear;
		tweenCompAP.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				BitmapFontComponent bfc = ComponentMappers.bitmapfont.get(e);
				bfc.color.a = Math.min(2.0f * a, 1);
			}
		};
		tweenComp.tweenSpecs.add(tweenCompAP);

		entity.add(tweenComp);

		return entity;
	}
}
