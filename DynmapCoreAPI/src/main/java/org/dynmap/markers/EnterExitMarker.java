package org.dynmap.markers;

public interface EnterExitMarker {
	public static class EnterExitText {
		public String title;
		public String subtitle;
	};
	/**
	 * Greeting text, if defined
	 */
	public EnterExitText getGreetingText();
	/**
	 * Farewell text, if defined
	 */
	public EnterExitText getFarewellText();
	/**
	 * Set greeting text
	 */
	public void setGreetingText(String title, String subtitle);
	/**
	 * Set greeting text
	 */
	public void setFarewellText(String title, String subtitle);
	/**
	 * Test if point is inside marker volume
	 */
	public boolean testIfPointWithinMarker(String worldid, double x, double y, double z);
};
