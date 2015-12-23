package com.apricotjam.spacepanic.screen;

import com.apricotjam.spacepanic.input.InputData;
import com.apricotjam.spacepanic.SpacePanic;
import com.apricotjam.spacepanic.input.InputManager;
import com.apricotjam.spacepanic.systems.RenderingSystem;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public abstract class BasicScreen implements Screen {

    protected final SpacePanic spacePanic;

    protected static final float CAMERA_WIDTH = 16.0f;
    protected static final float CAMERA_HEIGHT = 9.0f;

    protected int width;
    protected int height;

    protected OrthographicCamera worldCamera;
    protected SpriteBatch spriteBatch;

    private Engine engine;

    public BasicScreen(SpacePanic spacePanic) {
        this.spacePanic = spacePanic;
        worldCamera = new OrthographicCamera(CAMERA_WIDTH, CAMERA_HEIGHT);
        worldCamera.position.set(CAMERA_WIDTH / 2.0f, CAMERA_HEIGHT / 2.0f, 0);
        worldCamera.update();
        InputManager.screenInput.setCamera(worldCamera);

        engine = new Engine();
        spriteBatch = new SpriteBatch();
        engine.addSystem(new RenderingSystem(spriteBatch, worldCamera));
    }

    public void add(Entity e) {
        engine.addEntity(e);
    }

    public void add(EntitySystem es) {
        engine.addSystem(es);
    }

    public void render(float delta) {
        engine.update(delta);
    }

    @Override
    public void show() {
        worldCamera = new OrthographicCamera(CAMERA_WIDTH, CAMERA_HEIGHT);
        worldCamera.position.set(CAMERA_WIDTH / 2.0f, CAMERA_HEIGHT / 2.0f, 0);
        worldCamera.update();

        spriteBatch.setProjectionMatrix(worldCamera.combined);

        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    public boolean isOverlay() {
        return false;
    }

    public abstract void backPressed();
}
