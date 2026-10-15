package com.gabbymaybelle.beyondthedragon.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The test's handle on the game: work on the client thread, waits measured in client ticks, screens and screenshots. */
public final class ClientGameTestContext {
	private final Minecraft mc;

	ClientGameTestContext(Minecraft mc) {
		this.mc = mc;
	}

	public void waitTick() {
		waitTicks(1);
	}

	public void waitTicks(int ticks) {
		GameTestRunner.awaitTicks(ticks);
	}

	/** Waits, a tick at a time, until the client says so; an {@link AssertionError} when the ticks run out. */
	public void waitFor(Predicate<Minecraft> done, int maxTicks) {
		for (int waited = 0; ; waited++) {
			if (computeOnClient(done::test)) {
				return;
			}
			if (waited >= maxTicks) {
				throw new AssertionError("still waiting after " + maxTicks + " ticks");
			}
			waitTick();
		}
	}

	public <T extends Screen> void waitForScreen(Class<T> screen) {
		waitFor(mc -> screen.isInstance(mc.screen), 600);
	}

	public void runOnClient(Consumer<Minecraft> action) {
		computeOnClient(mc -> {
			action.accept(mc);
			return null;
		});
	}

	public <T> T computeOnClient(Function<Minecraft, T> action) {
		return mc.submit(() -> action.apply(mc)).join();
	}

	/** Presses the button on the open screen whose label is this translation key. */
	public void clickScreenButton(String translationKey) {
		runOnClient(mc -> {
			AbstractButton button = mc.screen == null ? null : findButton(mc.screen, translationKey);
			if (button == null) {
				throw new AssertionError("no button " + translationKey + " on " + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()));
			}
			button.onPress();
		});
	}

	private static AbstractButton findButton(ContainerEventHandler container, String translationKey) {
		for (GuiEventListener child : container.children()) {
			if (child instanceof AbstractButton button && button.getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(translationKey)) {
				return button;
			}
			if (child instanceof ContainerEventHandler nested) {
				AbstractButton found = findButton(nested, translationKey);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** A screenshot of the last frame, as {@code screenshots/<name>.png} in the run directory; returns the file once written. */
	public Path takeScreenshot(String name) {
		Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png");
		runOnClient(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {}));
		// The file is written off the render thread: wait until it exists and has stopped growing.
		long size = -1;
		for (int i = 0; i < 200; i++) {
			waitTick();
			long now = Files.exists(file) ? file.toFile().length() : -1;
			if (now > 0 && now == size) {
				break;
			}
			size = now;
		}
		return file;
	}

	public TestWorldBuilder worldBuilder() {
		return new TestWorldBuilder(this);
	}
}
