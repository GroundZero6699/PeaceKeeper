package peaceKeeper;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * @author Christoffer Wiik
 * @version 1.0
 * @since 2026-03-07
 * 
 * Opens a connection to microphone to read input and calculates sound levels.
 * */

public class SoundMonitor implements Runnable {
	private volatile float threshold;
	private final int buffer = 2048;
	private SoundListener listener;
	private volatile boolean running = true;
	private TargetDataLine line;
	
	/**
	 * Creates a sound monitor and
	 * initializes the threshold.
	 * @param sets threshold.
	 * */
	public SoundMonitor(float threshold) {
		this.threshold = threshold;
	}
	
	/**
	 * initializes the listener.
	 * @param listener interface.
	 * */
	public void setListener(SoundListener listener) {
		this.listener = listener;
	}
	
	/**
	 * updates the threshold.
	 * @param threshold current threshold for violation.
	 * */
	public void setThreshold(float threshold) {
		this.threshold = threshold;
	}
	
	/**
	 * Opens a data line to the microphone and starts to listen.
	 * @throw exception on error.
	 * */
	@Override
	public void run() {
		AudioFormat format = new AudioFormat(
				44100,
				16,
				1,
				true,
				false);
		
		try {
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
			line = (TargetDataLine) AudioSystem.getLine(info);
			line.open(format);
			line.start();
			
			byte[] buff = new byte[buffer];
			
			while(running) {
				int bytesRead = line.read(buff,  0,  buff.length);
				if(bytesRead > 0) {
					float rms = calculate(buff);

					float exceed = rms - threshold;
					
					if(listener != null) {
						listener.onRmsUpdate(rms);
					}
					
					if(rms > threshold && listener != null) {
						listener.onThreshold(rms, exceed);
					}
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * calculates the RMS value of a 16-bit PCM audio data.
	 * @param data 16-bit PCM audio data from microphone.
	 * @return a float value representing sound level as RMS.
	 * */
	private float calculate(byte[] data) {
		long sum = 0;
		for(int i = 0; i < data.length; i += 2) {
			int sample = (data[i + 1] << 8) | (data[i] & 0xFF);
			sum += sample * sample;
		}
		double mean = sum / (data.length / 2.0);
		return (float) Math.sqrt(mean);
	}
	
	/**
	 * Stops monitor from running.
	 * */
	public void stop() {
		running = false;
		if(line != null) {
			line.stop();
			line.close();
		}
	}
}
