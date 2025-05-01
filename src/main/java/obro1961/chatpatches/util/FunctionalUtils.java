package obro1961.chatpatches.util;

import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * A class containing various functional utilities
 */
public class FunctionalUtils {

	private int lastPos = 0, currentPos = 0;
	private float startTime = 0, currentTime = 0;

	public int getTimeLerpedQuantity(int targetPos, float smoothTime) {
		System.out.println("Changing");
        // ? Putting the animation smooth time to 0 disables smooth chat shifting
        if (smoothTime == 0) currentPos = targetPos;
        else if (currentPos == targetPos) {
            lastPos = currentPos;
            currentPos = targetPos;
        }
        // ? Check if we have not reached our destination yet, checking the position makes sure we are checking the rounded value
        // ? moving is used to ensure the starttime is only measured once right before beginning the animation
        else if (currentPos != targetPos && currentTime >= smoothTime) {
            currentTime = 0;
            startTime = Util.getMeasuringTimeMs();
            // ? If startTime == 0, then the game just launched and was loaded for the first time.
            // ? So, do not lerp and no need to go through the rest of the code
            if (startTime == 0) {
                return targetPos;
            }
            // moving = true;
        }
        else {
            currentTime = Util.getMeasuringTimeMs() - startTime;
            double t = currentTime / smoothTime;
            // ? Function used is SmootherStep
            currentPos =
            Math.round((float) MathHelper.lerp(
                    t * t * t * (t * (6.0f * t - 15.0f) + 10.0f),
                    lastPos,
                    targetPos));
        }

		return currentPos;
	}

}