package peaceKeeper;

/**
 * @author Christoffer wiik
 * @version 1.0
 * @since 2026-03-07
 * 
 * Interface that listen to sound monitor.
 * */
public interface SoundListener {
	void onRmsUpdate(float rms);
	void onThreshold(float rms, float exceed);
}
