package org.nypl.findawayclientlibrary.util;

import android.util.Log;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.BugsnagException;
import com.bugsnag.android.Severity;

// generated file in the build dir that knows whether we're running in debug mode


/**
 * Utility class to centralize logging configuration and control, from debugging to production.
 *
 * The original of this class comes from the Universal Android Music Player project,
 * where it's distributed under the Apache license.  Text of license announcement below.
 *
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class LogHelper {
    /** the maximum log tag length allowed by the operating system */
    private static final int MAX_LOG_TAG_LENGTH = 23;

    public static final byte DEV = 0;
    public static final byte PROD = 1;

    // TODO: switch before pushing
    //private static boolean SHOW_DEBUG = BuildConfig.DEBUG;

    // when running debugger in AndroidStudio, doesn't always know it's in debug mode
    // set manually, and remember to unset before pushing live.
    private static boolean SHOW_DEBUG = true;


    /**
     * An activity may call this method to see if a developer's eye-only Toast is to be displayed.
     * @return
     */
    public static byte getVerbosity() {
        if (SHOW_DEBUG) {
            return DEV;
        } else {
            return PROD;
        }
    }


    /**
     * Setter for the output verbosity level.
     * @param showDebug
     */
    public static void setShowDebug(boolean showDebug) {
        SHOW_DEBUG = showDebug;
    }


    /**
     * If passed-in log tag string is longer than allowed, then crop it to the first
     * X characters.  Else, return it unchanged.
     *
     * @param tagString the log tag string to check
     * @return
     */
    public static String cropLogTag(String tagString) {
        if (tagString == null) {
            return "";
        }
        if (tagString.length() > MAX_LOG_TAG_LENGTH) {
            return tagString.substring(0, MAX_LOG_TAG_LENGTH - 1);
        }

        return tagString;
    }


    /**
     * Make a verbose-level log message, and send it to the logger.
     *
     * @param tag
     * @param messages
     */
    public static void v(String tag, Object... messages) {
        // Creating the object to send to log takes resources,
        // so only craft a verbose log message if we're in debug mode.
        if (SHOW_DEBUG) {
            log(tag, Log.VERBOSE, null, messages);
        }
    }


    /**
     * Make a debug-level log message, and send it to the logger.
     *
     * @param tag
     * @param messages
     */
    public static void d(String tag, Object... messages) {
        // Creating the object to send to log takes resources,
        // so only craft a debug log message if we're in debug mode.
        if (SHOW_DEBUG) {
            log(tag, Log.DEBUG, null, messages);
        }
    }


    /**
     * Make an info-level log message, and send it to the logger.
     *
     * @param tag
     * @param messages
     */
    public static void i(String tag, Object... messages) {
        log(tag, Log.INFO, null, messages);
    }


    /**
     * Make a warning-level log message, and send it to the logger.
     *
     * @param tag
     * @param messages
     */
    public static void w(String tag, Object... messages) {
        log(tag, Log.WARN, null, messages);
    }


    /**
     * Make a warning-level log message, and send it to the logger.
     *
     * @param tag
     * @param t the exception that's being reported on
     * @param messages
     */
    public static void w(String tag, Throwable t, Object... messages) {
        log(tag, Log.WARN, t, messages);
    }


    /**
     * Make an error-level log message, and send it to the logger.
     *
     * @param tag
     * @param messages
     */
    public static void e(String tag, Object... messages) {
        log(tag, Log.ERROR, null, messages);
    }


    /**
     * Make an error-level log message, and send it to the logger.
     *
     * @param tag
     * @param t the exception that's being reported on
     * @param messages
     */
    public static void e(String tag, Throwable t, Object... messages) {
        log(tag, Log.ERROR, t, messages);
    }


    /**
     * Craft a message to send to the log.
     * See if the log is accepting being written to.
     * If yes, write the message to the log.
     * 
     * @param tag  identifier to preface the logging message with, for easier filtering
     * @param level  debug/info/warning/error
     * @param t  the exception that's being reported on
     * @param messages
     */
    public static void log(String tag, int level, Throwable t, Object... messages) {
        // if this is a baaad error, tell the online tracker BugSnag
        if (Log.ERROR == (level)) {
            if (t != null) {
                Bugsnag.notify(t, Severity.ERROR);
            } else {
                // we could move the call to BugSnag further down to take advantage of the composed message,
                // and then make sure all messages are composed all the time, and not using the isLoggable tag.
                // but StringBuilder ops take cpu cycles, and let's not go there unless there's a need.
                Bugsnag.notify(new BugsnagException("unknown_exception", tag, new StackTraceElement[]{}));
            }
        }

        // NOTE:  Can the system write to log?  This question should be easily and reliably answered
        // by calling Log.isLoggable().  And yet, the android OS and the Android Studio / emulator
        // environment do not do a good job of answering reliably.
        // There are two ways to get debug-level logging going through:
        // commenting out the isLoggable check will work for all logging on all classes,
        // and typing "adb shell setprop log.tag.abc VERBOSE", where
        // "abc" is the logging TAG you assign to a class, will work for that class.
        // TODO:  If do comment out the check, remember to uncomment before publishing live.
        //if (!Log.isLoggable(tag, level)) {
        // return;
        //}

        String message;
        if (t == null && messages != null && messages.length == 1) {
            // handle this common case without the extra cost of creating a stringbuffer:
            message = messages[0].toString();
        } else {
            StringBuilder sb = new StringBuilder();
            if (messages != null) for (Object m : messages) {
                if (m != null) {
                    sb.append(m);
                }
            }
            if (t != null) {
                sb.append("\n").append(Log.getStackTraceString(t));
            }
            message = sb.toString();
        }
        Log.println(level, tag, message);
    }
}
