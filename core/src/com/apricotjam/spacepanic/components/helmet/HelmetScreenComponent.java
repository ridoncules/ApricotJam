package com.apricotjam.spacepanic.components.helmet;

import com.apricotjam.spacepanic.gameelements.Resource;
import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.ObjectMap;

public class HelmetScreenComponent implements Component {
	public ObjectMap<Resource, Integer> resourceCount = new ObjectMap<Resource, Integer>();
	
	public HelmetScreenComponent() {
		resourceCount.put(Resource.OXYGEN, 25);
		resourceCount.put(Resource.OIL, 20);
		resourceCount.put(Resource.RESOURCE2, 15);
		resourceCount.put(Resource.RESOURCE3, 10);
	}
}