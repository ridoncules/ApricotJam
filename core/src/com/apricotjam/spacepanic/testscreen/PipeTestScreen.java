package com.apricotjam.spacepanic.testscreen;

import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.screen.BasicScreen;
import com.apricotjam.spacepanic.systems.AnimationSystem;
import com.apricotjam.spacepanic.systems.ClickSystem;
import com.apricotjam.spacepanic.systems.PipeSystem;
import com.apricotjam.spacepanic.systems.TweenSystem;

public class PipeTestScreen extends BasicScreen {

	public PipeTestScreen(SpacePanic spacePanic) {
		super(spacePanic);

		add(new PipeSystem());
		add(new ClickSystem());
		add(new TweenSystem());
		add(new AnimationSystem());
	}

	@Override
	public void backPressed() {
		// TODO Auto-generated method stub

	}
}