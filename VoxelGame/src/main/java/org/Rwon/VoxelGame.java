package org.Rwon;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.glfw.GLFWVidMode;

public class VoxelGame {
    private long window;

    private final Set<Block> blocks = new HashSet<>();
    private final List<Zombie> zombies = new ArrayList<>();
    private final Random random = new Random();

    private float playerX = 400;
    private float playerY = 300;
    private final float playerSize = 10;
    private boolean gameRunning = true;

    private int round = 1;
    private int zombiesToSpawn = 3; // Zombies in the current round
    private long lastRoundEndTime = 0;
    private boolean roundActive = false;

    private static class Block {
        int x, y;

        Block(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Block block = (Block) obj;
            return x == block.x && y == block.y;
        }

        @Override
        public int hashCode() {
            return x * 31 + y;
        }
    }

    private static class Zombie {
        float x, y;
        float speed = 1.5f;

        Zombie(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public void run() {
        System.out.println("Starting Voxel Game...");
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(800, 600, "Voxel Game", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidMode.width() - pWidth.get(0)) / 2, (vidMode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
    }

    private void loop() {
        GL.createCapabilities();

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 800, 600, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glClearColor(0.2f, 0.3f, 0.3f, 0.0f);

        lastRoundEndTime = System.currentTimeMillis();

        while (!glfwWindowShouldClose(window) && gameRunning) {
            glClear(GL_COLOR_BUFFER_BIT);

            handleInput();
            updateZombies();

            // Check if the round is over
            if (zombies.isEmpty() && roundActive) {
                roundActive = false;
                lastRoundEndTime = System.currentTimeMillis();
                round++;
                zombiesToSpawn = round * 3; // Increase the number of zombies each round
                System.out.println("Round " + round + " will start soon!");
            }

            // Start the next round after a short delay
            if (!roundActive && System.currentTimeMillis() - lastRoundEndTime > 3000) {
                spawnRound();
                roundActive = true;
            }

            renderBlocks();
            renderZombies();
            renderPlayer();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        if (!gameRunning) {
            System.out.println("Game Over! You were killed by a zombie.");
        }
    }

    private void handleInput() {
        float speed = 2.0f;
        float newPlayerX = playerX;
        float newPlayerY = playerY;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) newPlayerY -= speed;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) newPlayerY += speed;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) newPlayerX -= speed;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) newPlayerX += speed;

        Block newBlock = new Block((int) (newPlayerX / 20), (int) (newPlayerY / 20));
        if (!blocks.contains(newBlock)) {
            playerX = newPlayerX;
            playerY = newPlayerY;
        }

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            Block block = getMouseBlock();
            blocks.add(block);
        }

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
            Block block = getMouseBlock();
            blocks.remove(block);
        }
    }

    private void updateZombies() {
        for (Iterator<Zombie> iterator = zombies.iterator(); iterator.hasNext(); ) {
            Zombie zombie = iterator.next();

            // Move towards the player
            float dx = playerX - zombie.x;
            float dy = playerY - zombie.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            zombie.x += (dx / distance) * zombie.speed;
            zombie.y += (dy / distance) * zombie.speed;

            // Check collision with blocks
            Block block = new Block((int) (zombie.x / 20), (int) (zombie.y / 20));
            if (blocks.contains(block)) {
                if (random.nextInt(100) < 5) blocks.remove(block); // 5% chance to remove block
                iterator.remove(); // Remove zombie on collision
                continue;
            }

            // Check collision with player
            float playerDistance = (float) Math.sqrt((zombie.x - playerX) * (zombie.x - playerX) +
                    (zombie.y - playerY) * (zombie.y - playerY));
            if (playerDistance < playerSize + 10) {
                gameRunning = false; // End game
                return;
            }
        }
    }

    private void spawnRound() {
        System.out.println("Spawning round " + round + " with " + zombiesToSpawn + " zombies!");
        for (int i = 0; i < zombiesToSpawn; i++) {
            spawnZombie();
        }
    }

    private void spawnZombie() {
        // Spawn a zombie at a random edge of the screen
        int edge = random.nextInt(4);
        float x = 0, y = 0;
        switch (edge) {
            case 0 -> { x = 0; y = random.nextFloat() * 600; }  // Left
            case 1 -> { x = 800; y = random.nextFloat() * 600; } // Right
            case 2 -> { x = random.nextFloat() * 800; y = 0; }   // Top
            case 3 -> { x = random.nextFloat() * 800; y = 600; } // Bottom
        }
        zombies.add(new Zombie(x, y));
    }

    private Block getMouseBlock() {
        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        glfwGetCursorPos(window, mouseX, mouseY);

        int gridX = (int) (mouseX[0] / 20);
        int gridY = (int) (mouseY[0] / 20);
        return new Block(gridX, gridY);
    }

    private void renderBlocks() {
        for (Block block : blocks) {
            glColor3f(0.8f, 0.8f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(block.x * 20, block.y * 20);
            glVertex2f(block.x * 20 + 20, block.y * 20);
            glVertex2f(block.x * 20 + 20, block.y * 20 + 20);
            glVertex2f(block.x * 20, block.y * 20 + 20);
            glEnd();
        }
    }

    private void renderPlayer() {
        glColor3f(1.0f, 0.0f, 0.0f);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(playerX, playerY);
        for (int i = 0; i <= 20; i++) {
            double angle = 2 * Math.PI * i / 20;
            glVertex2f(playerX + playerSize * (float) Math.cos(angle), playerY + playerSize * (float) Math.sin(angle));
        }
        glEnd();
    }

    private void renderZombies() {
        glColor3f(0.0f, 1.0f, 0.0f);
        for (Zombie zombie : zombies) {
            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(zombie.x, zombie.y);
            for (int i = 0; i <= 20; i++) {
                double angle = 2 * Math.PI * i / 20;
                glVertex2f(zombie.x + 10 * (float) Math.cos(angle), zombie.y + 10 * (float) Math.sin(angle));
            }
            glEnd();
        }
    }

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new VoxelGame().run();
    }
}