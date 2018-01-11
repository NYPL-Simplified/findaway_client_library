package org.nypl.findawayclientlibrary.util;

/**
 * Created by daryachernikhova on 1/8/18.
 */

public class DateTimeUtil {

  /**
   * Makes a time in millis be presentable to a UI, s.a. when labeling seekbar positions.
   *
   * @param timePosition
   * @return
   */
  public static String millisToHumanReadable(long timePosition) {
    StringBuffer buf = new StringBuffer();
    int hours = (int)(timePosition / 3600000L);
    int minutes = (int)(timePosition % 3600000L / 60000L);
    int seconds = (int)(timePosition % 3600000L % 60000L / 1000L);

    String humanReadable = String.format("%02d", new Object[]{Integer.valueOf(minutes)}) + ":" + String.format("%02d", new Object[]{Integer.valueOf(seconds)});

    if (hours != 0) {
      humanReadable = String.format("%02d", new Object[]{Integer.valueOf(hours)}) + ":" + humanReadable;
    }

    return humanReadable;
  }

}
