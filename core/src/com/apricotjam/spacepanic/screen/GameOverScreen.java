package com.apricotjam.spacepanic.screen;

import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.components.BitmapFontComponent;
import com.apricotjam.spacepanic.components.ComponentMappers;
import com.apricotjam.spacepanic.components.TransformComponent;
import com.apricotjam.spacepanic.components.TweenComponent;
import com.apricotjam.spacepanic.components.TweenSpec;
import com.apricotjam.spacepanic.interfaces.TweenInterface;
import com.apricotjam.spacepanic.systems.ClickSystem;
import com.apricotjam.spacepanic.systems.MovementSystem;
import com.apricotjam.spacepanic.systems.RenderingSystem;
import com.apricotjam.spacepanic.systems.ScrollSystem;
import com.apricotjam.spacepanic.systems.TweenSystem;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Interpolation;

public class GameOverScreen extends BasicScreen {

	public GameOverScreen(SpacePanic spacePanic, Entity backgroundEntity) {
		super(spacePanic);

		add(new RenderingSystem(spriteBatch, worldCamera));
		add(new ClickSystem());
		add(new TweenSystem());
		add(new MovementSystem());
		add(new ScrollSystem());
		
		add(backgroundEntity);
		add(createGameOver());
	}
	
	private Entity createGameOver() {
		Entity entity = new Entity();
		
		BitmapFontComponent fontComp = new BitmapFontComponent();
		fontComp.font = "retro";
		fontComp.string = "GAME OVER";
		fontComp.color.set(1f, 1f, 1f, 0f);
		fontComp.centering = true;
		entity.add(fontComp);
		
		TransformComponent transComp = new TransformComponent();
		transComp.position.x = BasicScreen.WORLD_WIDTH / 2f;
		transComp.position.y = BasicScreen.WORLD_HEIGHT / 2f;
		entity.add(transComp);
		
		TweenComponent tweenComponent = new TweenComponent();
		TweenSpec tweenSpec = new TweenSpec();
		tweenSpec.start = 0f;
		tweenSpec.end = 1.0f;
		tweenSpec.period = 2f;
		tweenSpec.interp = Interpolation.linear;
		tweenSpec.cycle = TweenSpec.Cycle.ONCE;
		tweenSpec.tweenInterface = new TweenInterface() {
			@Override
			public void applyTween(Entity e, float a) {
				BitmapFontComponent bitmapFontComponent = ComponentMappers.bitmapfont.get(e);
				bitmapFontComponent.color.a = Math.max(a, 0f);
			}
		};
		tweenComponent.tweenSpecs.add(tweenSpec);
		entity.add(tweenComponent);
		
		return entity;
	}

	@Override
	public void backPressed() {
		// TODO Auto-generated method stub
		
	}

}