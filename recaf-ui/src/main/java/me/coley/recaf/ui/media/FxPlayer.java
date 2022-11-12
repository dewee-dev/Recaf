package me.coley.recaf.ui.media;

import com.sun.media.jfxmediaimpl.NativeMediaManager;
import javafx.scene.media.AudioSpectrumListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import me.coley.recaf.util.RecafURLStreamHandlerProvider;
import me.coley.recaf.util.ReflectUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * An media-player using JavaFX's {@link MediaPlayer} with the use of {@link RecafURLStreamHandlerProvider}
 * which allows pulling audio from 'memory' via the current {@link me.coley.recaf.workspace.Workspace}.
 *
 * @author Matt Coley
 */
public class FxPlayer extends Player implements AudioSpectrumListener {
	private SpectrumEvent eventInstance;
	private MediaPlayer player;
	Media media;

	@Override
	public void play() {
		if (player != null) {
			player.play();
			player.setAudioSpectrumListener(this);
		}
	}

	@Override
	public void pause() {
		// TODO: Pausing and then unpausing sometimes causes the spectrum listener to feel 'laggy'
		//  - but the spectrum interval is still the same so its not like that is getting reset
		//  - happens more consistently with mp3 files, m4a is seemingly unaffected
		//  - and pausing/unpausing can sometimes also fix the 'laggy' feeling.
		if (player != null) {
			player.pause();
			player.setAudioSpectrumListener(null);
		}
	}

	@Override
	public void stop() {
		if (player != null) {
			// Stop is supposed to call 'seek(0)' in implementation but for some reason it is not consistent.
			// Especially if the current state is 'paused'. If we request the seek ourselves it *seems* more reliable.
			player.seek(Duration.ZERO);
			player.stop();
			player.setAudioSpectrumListener(null);
			// Reset spectrum data
			SpectrumListener listener = getSpectrumListener();
			if (listener != null && eventInstance != null) {
				Arrays.fill(eventInstance.getMagnitudes(), -100);
				listener.onSpectrum(eventInstance);
			}
		}
	}

	@Override
	public void reset() {
		stop();
		media = null;
		player = null;
	}

	@Override
	public void load(String path) throws IOException {
		try {
			media = MediaHacker.create(path);
			player = new MediaPlayer(media);
			player.audioSpectrumIntervalProperty().set(0.04);
			player.setAudioSpectrumListener(this);
		} catch (Exception ex) {
			reset();
			throw new IOException("Failed to load content from: " + path, ex);
		}
	}

	@Override
	public void spectrumDataUpdate(double timestamp, double duration, float[] magnitudes, float[] phases) {
		SpectrumListener listener = getSpectrumListener();
		if (listener != null) {
			if (eventInstance == null || eventInstance.getMagnitudes().length != magnitudes.length)
				eventInstance = new SpectrumEvent(magnitudes);
			else
				System.arraycopy(magnitudes, 0, eventInstance.getMagnitudes(), 0, magnitudes.length);
			listener.onSpectrum(eventInstance);
		}
	}

	@Override
	public double getMaxSeconds() {
		if (player != null)
			return player.getTotalDuration().toSeconds();
		return -1;
	}

	@Override
	public double getCurrentSeconds() {
		if (player != null)
			return player.getCurrentTime().toSeconds();
		return -1;
	}

	/**
	 * @return Current player for {@link #getMedia() media}.
	 */
	public MediaPlayer getPlayer() {
		return player;
	}

	/**
	 * @return Current loaded media.
	 */
	public Media getMedia() {
		return media;
	}

	/**
	 * @return {@code true} when there is content loaded in the player.
	 */
	public boolean hasContent() {
		return player != null;
	}

	static {
		RecafURLStreamHandlerProvider.install();
		try {
			// Inject protocol name in manager
			NativeMediaManager manager = NativeMediaManager.getDefaultInstance();
			Field fProtocols = ReflectUtil.getDeclaredField(NativeMediaManager.class, "supportedProtocols");
			fProtocols.setAccessible(true);
			List<String> protocols = ReflectUtil.quietGet(manager, fProtocols);
			protocols.add(RecafURLStreamHandlerProvider.recafFile);
			// Inject protocol name into platform impl
			Class<?> platformImpl = Class.forName("com.sun.media.jfxmediaimpl.platform.gstreamer.GSTPlatform");
			fProtocols = platformImpl.getDeclaredField("PROTOCOLS");
			fProtocols.setAccessible(true);
			String[] protocolArray = (String[]) fProtocols.get(null);
			String[] protocolArrayPlus = new String[protocolArray.length + 1];
			System.arraycopy(protocolArray, 0, protocolArrayPlus, 0, protocolArray.length);
			protocolArrayPlus[protocolArray.length] = RecafURLStreamHandlerProvider.recafFile;
			// Required for newer versions of Java
			ReflectUtil.unsafePut(fProtocols, protocolArrayPlus);
		} catch (Throwable t) {
			throw new IllegalStateException("Could not hijack platforms to support recaf URI protocol", t);
		}
	}
}
