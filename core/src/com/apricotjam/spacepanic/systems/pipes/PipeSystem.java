package com.apricotjam.spacepanic.systems.pipes;

import com.apricotjam.spacepanic.components.ClickComponent;
import com.apricotjam.spacepanic.components.ComponentMappers;
import com.apricotjam.spacepanic.components.StateComponent;
import com.apricotjam.spacepanic.components.pipe.PipeFluidComponent;
import com.apricotjam.spacepanic.components.pipe.PipeScreenComponent;
import com.apricotjam.spacepanic.components.pipe.PipeTileComponent;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;

public class PipeSystem extends EntitySystem {
	private ImmutableArray<Entity> pipeFluids, pipeTiles;
	private PipeWorld world;
	private boolean startedSolutionAnimation = false;
	private float solvedFluidSpeedup = 32f;
	
	private Entity masterEntity;
	
	public PipeSystem(Entity masterEntity, int difficulty) {
		this.masterEntity = masterEntity;
		world = new PipeWorld(masterEntity, difficulty);
	}

	@Override
	public void addedToEngine(Engine engine) {
		world.build(engine);
		pipeTiles = engine.getEntitiesFor(Family.all(PipeTileComponent.class, ClickComponent.class).get());
		pipeFluids = engine.getEntitiesFor(Family.all(PipeFluidComponent.class).get());
	}
	
	@Override
	public void removedFromEngine(Engine engine) {
		for (Entity entity : world.getAllPipeEntities())
			engine.removeEntity(entity);
		for (Entity entity : pipeFluids)
			engine.removeEntity(entity);
	}

	@Override
	public void update(float deltaTime) {
		PipeScreenComponent pipeScreenComp = ComponentMappers.pipescreen.get(masterEntity);
		
		if (pipeScreenComp.currentState == PipeScreenComponent.State.PLAYING) {
			boolean finishedSolutionAnimation = true;
			
			for (Entity pipeFluid : pipeFluids) {
				PipeFluidComponent pipeFluidComp = ComponentMappers.pipefluid.get(pipeFluid);
				StateComponent stateComp = ComponentMappers.state.get(pipeFluid);
				pipeFluidComp.currFill += stateComp.timescale*deltaTime;
				
				if (pipeFluidComp.filling && startedSolutionAnimation)
					finishedSolutionAnimation = false;
				
				if (pipeFluidComp.filling && pipeFluidComp.currFill >= pipeFluidComp.fillDuration) { // Pipe is full.
					pipeFluidComp.filling = false;
					
					// Find next pipe. If not connected, player fails; else start next pipe filling and prevent user from rotating it.
					int exitDirection = PipeWorld.directionFromMask(pipeFluidComp.exitMask);
					PipeTileComponent currPipeTileComp = ComponentMappers.pipetile.get(pipeFluidComp.parentPipe);
					Entity nextPipe = currPipeTileComp.neighbours[exitDirection];
					
					if (nextPipe != null) {
						PipeTileComponent nextPipeTileComp = ComponentMappers.pipetile.get(nextPipe);
						
						int entryDirection = PipeWorld.oppositeDirectionIndex(exitDirection);
						
						if (PipeWorld.connectedAtIndex(nextPipeTileComp.mask, entryDirection) && !PipeWorld.connectedAtIndex(nextPipeTileComp.usedExitMask, entryDirection)) {
							// Next pipe is connected, start filling.
							Entity nextFluid = world.createFluid(nextPipe, entryDirection);
							ComponentMappers.shadertime.get(nextFluid).time = ComponentMappers.shadertime.get(pipeFluid).time;
							if (startedSolutionAnimation) {
								StateComponent nextStateComp = ComponentMappers.state.get(nextFluid);
								nextStateComp.timescale = solvedFluidSpeedup;
							}
							getEngine().addEntity(nextFluid);
							
							// Stop player rotating the filling pipe.
							ClickComponent clickComp = ComponentMappers.click.get(nextPipe);
							clickComp.active = false;
							
							// Set exit mask to prevent fluid collisions.
							nextPipeTileComp.usedExitMask = PipeWorld.connectAtIndex(nextPipeTileComp.usedExitMask, PipeWorld.exitFromEntryDirection(nextPipeTileComp.mask, entryDirection));
						}
						else {
							if (!startedSolutionAnimation)
								failed();
						}
					}
					else {
						if (!startedSolutionAnimation)
							failed();
					}
				}
			}
			
			if (startedSolutionAnimation && finishedSolutionAnimation) {
				pipeScreenComp.currentState = PipeScreenComponent.State.SUCCESS;
			}
			else if (!startedSolutionAnimation) {
				boolean isSolved = true;
				
				for (int ipipe = 0; ipipe < world.getEntryPoints().size; ++ipipe) {
					Entity currPipe = world.getEntryPipes().get(ipipe);
					PipeTileComponent currPipeTileComp = ComponentMappers.pipetile.get(currPipe);
					int currExitDirection = PipeWorld.directionFromMask(currPipeTileComp.mask);
					
					boolean isCurrPipeSolved = false;
					
					while (!isCurrPipeSolved) {
						currPipe = currPipeTileComp.neighbours[currExitDirection];
						if (currPipe == null)
							break;
						else if (currPipe == world.getExitPipes().get(ipipe)) {
							isCurrPipeSolved = true;
							break;
						}
						else {
							currPipeTileComp = ComponentMappers.pipetile.get(currPipe);
							int currEntryDirection = PipeWorld.oppositeDirectionIndex(currExitDirection);
							
							if (!PipeWorld.connectedAtIndex(currPipeTileComp.mask, currEntryDirection))
								break;
							else
								currExitDirection = PipeWorld.exitFromEntryDirection(currPipeTileComp.mask, currEntryDirection);
						}
					}
					
					if (!isCurrPipeSolved) {
						isSolved = false;
						break;
					}	
				}
				
				if (isSolved) {
					solved();
				}
			}
		}
	}
	
	public void start() {
		// Add fluid entities.
		for (Entity pipeTile : world.getEntryPipes()) {
			PipeTileComponent pipeTileComp = ComponentMappers.pipetile.get(pipeTile);			
			int entryDirection = PipeWorld.oppositeDirectionIndex(PipeWorld.directionFromMask(pipeTileComp.mask));
			getEngine().addEntity(world.createFluid(pipeTile, entryDirection));
		}
		
		// Set clickable to active so user can rotate tiles.
		for (Entity pipeTile : pipeTiles) {
			ClickComponent clickComp = ComponentMappers.click.get(pipeTile);
			clickComp.active = true;
		}
		
		// Start timer.
		//TickerComponent timerTickerComp = ComponentMappers.ticker.get(world.getTimer());
		//timerTickerComp.start();
		
		// Set screen state.
		PipeScreenComponent pipeScreenComp = ComponentMappers.pipescreen.get(masterEntity);
		pipeScreenComp.currentState = PipeScreenComponent.State.PLAYING;
	}
	
	public void solved() {
		// Prevent user from rotating tiles.
		for (Entity pipeTile : pipeTiles) {
			ClickComponent clickComp = ComponentMappers.click.get(pipeTile);
			clickComp.active = false;
		}
		// Speed up the fluid filling.
		for (Entity pipeFluid : pipeFluids) {
			StateComponent stateComp = ComponentMappers.state.get(pipeFluid);
			stateComp.timescale = solvedFluidSpeedup;
		}
		// Stop timer;
		//TickerComponent timerTickerComp = ComponentMappers.ticker.get(world.getTimer());
		//timerTickerComp.tickerActive = false;
		//timerTickerComp.finishActive = false;
		
		startedSolutionAnimation = true;
	}
	
	public void failed() {
		// Prevent user from rotating tiles.
		for (Entity pipeTile : pipeTiles) {
			ClickComponent clickComp = ComponentMappers.click.get(pipeTile);
			clickComp.active = false;
		}
		// Stop timer;
		//TickerComponent timerTickerComp = ComponentMappers.ticker.get(world.getTimer());
		//timerTickerComp.tickerActive = false;
		//timerTickerComp.finishActive = false;
		
		PipeScreenComponent pipeScreenComp = ComponentMappers.pipescreen.get(masterEntity);
		pipeScreenComp.currentState = PipeScreenComponent.State.FAIL;
	}
}
