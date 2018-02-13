package org.nypl.findawayclientlibrary;

import android.content.res.AssetManager;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.bugsnag.android.BreadcrumbType;
import com.bugsnag.android.Bugsnag;

import org.nypl.audiobookincludes.util.DateTimeUtil;
import org.nypl.audiobookincludes.util.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;



/**
 * Loads the UI with media controls, audiobook metadata s.a. title, author, cover image,
 * and on-screen playback settings.
 *
 * Responds to user click events that affect audio playback, but asks the calling activity
 * to communicate with the audio player service.
 *
 * Created by daryachernikhova on 7/21/17.
 */
public class PlayBookFragment extends BaseFragment {

  // so can do a search in log msgs for just this class's output
  private final String TAG = APP_TAG + "PlayBookFragment";

  private View fragmentView = null;

  private ImageView coverImage;

  private FloatingActionButton downloadButton;
  // non-user-interactive, usually used to show download progress
  private ProgressBar downloadProgress;
  private TextView chapterPercentage, contentPercentage;

  private TextView currentTime, remainingTime;
  private FloatingActionButton playButton;
  //private ImageButton previousButton, nextButton;
  private ImageButton backButton, forwardButton;
  private ToggleButton playbackSpeedButton;

  // interactive, both shows progress and allows user to control
  private SeekBar playbackSeekBar;



  /* ---------------------------------- LIFECYCLE METHODS ----------------------------------- */
  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    // do not call super, as it returns a null, prevents onViewCreated from auto-running, and prevents getView() from working later in the code.
    //return super.onCreateView(inflater, container, savedInstanceState);

    return inflater.inflate(R.layout.findaway_fragment_play_book, container, false);
  }


  /**
   * Initializes the view, adding onClick events to the view child elements.
   *
   * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
   * View hierarchy has been completely created, but not attached to its parent at this point.
   * NOTE: Will not called automatically if you are returning null or super.onCreateView() from onCreateView().
   * NOTE: Can get the fragment view anywhere in the class by using getView() once onCreateView() has been executed successfully.
   *
   * @param view
   * @param savedInstanceState
   */
  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // save the view handle, for future convenience
    fragmentView = view;

    initializeControlsUI();

    initializeDefaultCover();
  }


  /**
   * Hooking up media controls to UI play/pause/etc. buttons goes here.
   */
  private void initializeControlsUI() {
    if (fragmentView == null) {
      // something is very very wrong, we cannot proceed
      LogHelper.e(TAG, "initializeControlsUI() encountered a null fragmentView");
      throw new Error(TAG + ": initializeControlsUI() encountered a null fragmentView");
    }

    // set up the UI elements that will give download info
    downloadButton = (FloatingActionButton) fragmentView.findViewById(R.id.download_button);
    downloadProgress = (ProgressBar) fragmentView.findViewById(R.id.download_progress);

    //chapterPercentage = (TextView) fragmentView.findViewById(R.id.chapter_download_percentage);
    //contentPercentage = (TextView) fragmentView.findViewById(R.id.content_download_percentage);

    // tell the download buttons the parent activity will be listening to them
    //downloadButton.setOnClickListener((View.OnClickListener) callbackActivity);
    //downloadButton.setOnLongClickListener((View.OnLongClickListener) callbackActivity);

    // set up the UI elements that will give playback info
    //previousButton = (ImageButton) fragmentView.findViewById(R.id.previous_track_button);
    backButton = (ImageButton) fragmentView.findViewById(R.id.rewind_button);
    playButton = (FloatingActionButton) fragmentView.findViewById(R.id.play_pause_button);
    forwardButton = (ImageButton) fragmentView.findViewById(R.id.forward_button);
    //nextButton = (ImageButton) fragmentView.findViewById(R.id.next_track_button);
    playbackSeekBar = (SeekBar) fragmentView.findViewById(R.id.playback_seek_bar);
    //playbackSpeedButton = (ToggleButton) fragmentView.findViewById(R.id.playback_speed_button);

    currentTime = (TextView) fragmentView.findViewById(R.id.current_playback_position);
    remainingTime = (TextView) fragmentView.findViewById(R.id.playback_time_remaining);

    // tell the playback buttons the parent activity will be listening to them
    //previousButton.setOnClickListener((View.OnClickListener) callbackActivity);
    backButton.setOnClickListener((View.OnClickListener) callbackActivity);
    playButton.setOnClickListener((View.OnClickListener) callbackActivity);
    forwardButton.setOnClickListener((View.OnClickListener) callbackActivity);
    //nextButton.setOnClickListener((View.OnClickListener) callbackActivity);
    playbackSeekBar.setOnSeekBarChangeListener((SeekBar.OnSeekBarChangeListener) callbackActivity);
    //playbackSpeedButton.setOnClickListener((View.OnClickListener) callbackActivity);

    playButton.setTag(getResources().getString(R.string.play));

  }// initializeControlsUI



  /**
   * If a book doesn't have a cover image, this sets up the default image to be displayed.
   * Currently, the default is a gradient animation that's the background of the cover image-holding view.
   * When a cover image is loaded, it layers on top of the animation.
   */
  private void initializeDefaultCover() {
    if (fragmentView == null) {
      // something is very very wrong, and not just with the imge display area; we cannot proceed
      LogHelper.e(TAG, "initializeDefaultCover() encountered a null fragmentView");
      throw new Error(TAG + ": initializeDefaultCover() encountered a null fragmentView");
    }

    View middlePane = fragmentView.findViewById(R.id.middle_info_area_background);
    if (middlePane == null) {
      // should never happen, but app shouldn't crash on cover image processing
      LogHelper.e(TAG, "initializeDefaultCover() encountered a null middlePane");
      return;
    }
    AnimationDrawable animationDrawable = (AnimationDrawable) middlePane.getBackground();
    if (animationDrawable == null) {
      // should never happen, but app shouldn't crash on cover image processing
      LogHelper.e(TAG, "initializeDefaultCover() encountered a null animationDrawable");
      return;
    }
    animationDrawable.setEnterFadeDuration(4000);
    animationDrawable.setExitFadeDuration(4000);

    // make sure to only call start animation once the animation is fully attached to the view
    animationDrawable.start();

    // TODO: move the call for loadCoverImage() from here to the method that will load the book's metadata in a later branch.
    loadCoverImage();
  }


  /**
   * Set the display to reflect whether we're reading at increased speed.
   *
   * @param checked
   */
  public void redrawSpeedButton(boolean checked) {
    // TODO
    //playbackSpeedButton.setChecked(checked);
  }

  /* ---------------------------------- /LIFECYCLE METHODS ----------------------------------- */



  /* ------------------------------------ NAVIGATION EVENT HANDLERS ------------------------------------- */

  /**
   * Load a book cover image, properly scaled, into the middle view pane.
   * TODO:  in future branch, this should get populated from loaded metadata.
   */
  public void loadCoverImage() {
    // leave note for BugSnag that we've tried to load an image
    Bugsnag.leaveBreadcrumb(getString(R.string.logtag_cover_image), BreadcrumbType.NAVIGATION, new HashMap<String, String>());

    if (fragmentView == null) {
      // something is very very wrong, and not just with the image display area; we cannot proceed
      LogHelper.e(TAG, "loadCoverImage() encountered a null fragmentView");
      throw new Error(TAG + ": loadCoverImage() encountered a null fragmentView");
    }

    coverImage = (ImageView) fragmentView.findViewById(R.id.cover_image);
    if (coverImage == null) {
      // you know what?  missing a piece of UI scenery is probably a symptom of a larger problem,
      // and definitely should not happen, but ultimately, we can play a book without displaying a cover.
      // exit the method, and don't throw an exception
      LogHelper.e(TAG, "loadCoverImage() encountered a null coverImage");
      return;
    }

    //Uri myUri = Uri.parse("file:///android_asset/a21_gun_salute/a1752599_001_c001.mp3"); // initialize Uri here
    String coverImageFilePath = "21_gun_salute/1752599_image_512x512_iTunes.png";

    // NOTE:  Library modules cannot include raw assets, which are expected to live in the containing app.
    AssetManager assetManager = getResources().getAssets();
    InputStream coverImageStream = null;

    try {
      coverImageStream = assetManager.open(coverImageFilePath);

      // load image as Drawable
      Drawable coverImageDrawable = Drawable.createFromStream(coverImageStream, "Book Cover Image");
      if (coverImageDrawable != null) {
        coverImage.setImageDrawable(coverImageDrawable);

        coverImageDrawable.getBounds();
      }
    } catch (IOException e) {
      // throw a quick unobtrusive toast, and a descriptive log message
      Toast.makeText(getContext(), R.string.cannot_load_image_file, Toast.LENGTH_LONG).show();
      LogHelper.e(TAG, e, "loadCoverImage() could not read from file.");
    } finally {
      if (coverImageStream != null) {
        try {
          coverImageStream.close();
        } catch (IOException e) {
          // user doesn't need to see this one, only print to log
          LogHelper.e(TAG, e, "loadCoverImage() could not close coverImageStream.");
        }
      }
    }

  } //loadCoverImage


  /* ------------------------------------ /NAVIGATION EVENT HANDLERS ------------------------------------- */



  /* ------------------------------------ DOWNLOAD EVENT HANDLERS ------------------------------------- */

  /**
   * Change the message on the download button, letting the user know where we are in the downloading progress.
   */
  public void redrawDownloadButton(DownloadService.DOWNLOAD_STATUS status) {
    if (status == null) {
      return;
    }

    if (status.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_RUNNING)) {
      //downloadButton.setText(getString(R.string.pause));
      return;
    }

    if (status.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_PAUSED)) {
      //downloadButton.setText(getString(R.string.resume));
      return;
    }

    if (status.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_STOPPED)) {
      //downloadButton.setText(getString(R.string.start));
      return;
    }

    if (status.equals(DownloadService.DOWNLOAD_STATUS.DOWNLOAD_ERROR)) {
      //downloadButton.setText(getString(R.string.alert));
      return;
    }
  }


  /**
   * Update the progress bar to reflect where we are in the downloading.
   * While both the primary and secondary progress percentages are being passed in,
   * ignore the secondary, and only update the primary for now.  Updating the chapters
   * is happening too much and thrashing the UI.  To use the secondaryProgress as per chapter
   * download indicators, will want to take the UI update onto a separate thread.
   *
   * @param primaryProgress Represents percent of total book download.
   * @param secondaryProgress Represents percent of currently downloading chapter.
   */
  public void redrawDownloadProgress(Integer primaryProgress, Integer secondaryProgress) {
    downloadProgress.setProgress(primaryProgress);
    // downloadProgress.setSecondaryProgress(secondaryProgress);

    // textview feedback of percentages will either go away completely, or move to another location in UI
    //contentPercentage.setText(getString(R.string.contentPercentage, primaryProgress));
    //chapterPercentage.setText(getString(R.string.chapterPercentage, secondaryProgress));
  }


  public void resetDownloadProgress() {
    this.redrawDownloadProgress(0, 0);
  }
  /* ------------------------------------ /DOWNLOAD EVENT HANDLERS ------------------------------------- */



  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */

  /**
   * Change message and pic on play button to match current state.
   *
   * @param newText
   * @param newImageId
   */
  public void redrawPlayButton(String newText, int newImageId) {
    // TODO: check that code works on kitkat.  if yes, then don't need the "if (checkAndroidVersion() < Build.VERSION_CODES.LOLLIPOP) {"
    // line in audiobookplaylibrary/PlayBookFragment.  if no, then need that line here.
    playButton.setImageResource(newImageId);
    playButton.setTag(newText);
  }


  /**
   * Move the seek bar to reflect the current playback position within the book chapter.
   * Making sure the passed event is of type progress update happens in the calling code.
   *
   * @param duration
   * @param position
   */
  public void redrawPlaybackPosition(Long duration, Long position) {
    // TODO: intValue may overflow, and 0 may not be best default value.  fix to handle.
    int max = duration != null ? duration.intValue() : 0;
    int progress = position != null ? position.intValue() : 0;
    playbackSeekBar.setMax(max);
    playbackSeekBar.setProgress(progress);

    currentTime.setText(DateTimeUtil.millisToHumanReadable(progress));
    remainingTime.setText(DateTimeUtil.millisToHumanReadable(max - progress));
  }


  /**
   * Move the audio playback position in response to user shifting the seek bar.
   *
   * @param seekBar
   * @param progress
   * @param fromUser
   */
  public void redrawPlaybackPosition(SeekBar seekBar, int progress, boolean fromUser) {
    if (fromUser) {
      if (seekBar.getId() == this.playbackSeekBar.getId()) {
        currentTime.setText(DateTimeUtil.millisToHumanReadable(progress));

        // TODO: need to update remaining time?  can combine with method above?
      }
    }
  }


  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */

}
