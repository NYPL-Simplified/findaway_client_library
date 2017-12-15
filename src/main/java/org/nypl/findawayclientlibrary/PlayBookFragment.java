package org.nypl.findawayclientlibrary;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import io.audioengine.mobile.DownloadEvent;
import io.audioengine.mobile.PlaybackEvent;
import io.audioengine.mobile.util.StringUtils;


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
  // so can filter all log msgs belonging to my app
  private final String APP_TAG = "FDLIB.";

  // so can do a search in log msgs for just this class's output
  private final String TAG = APP_TAG + "PlayBookFragment";

  private View fragmentView = null;

  private Button downloadButton;
  // non-user-interactive, usually used to show download progress
  private ProgressBar downloadProgress;
  private TextView chapterPercentage, contentPercentage;

  private TextView currentTime, remainingTime;
  private ImageButton previousButton, backButton, playButton, forwardButton, nextButton;
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

    initializeControlsUI(view);
  }


  /**
   * Hooking up media controls to UI play/pause/etc. buttons goes here.
   *
   * @param view
   */
  private void initializeControlsUI(View view) {
    // set up the UI elements that will give download info
    downloadButton = (Button) fragmentView.findViewById(R.id.download_button);
    downloadProgress = (ProgressBar) fragmentView.findViewById(R.id.download_progress);
    chapterPercentage = (TextView) fragmentView.findViewById(R.id.chapter_download_percentage);
    contentPercentage = (TextView) fragmentView.findViewById(R.id.content_download_percentage);

    // tell the download buttons the parent activity will be listening to them
    downloadButton.setOnClickListener((View.OnClickListener) callbackActivity);
    downloadButton.setOnLongClickListener((View.OnLongClickListener) callbackActivity);

    // set up the UI elements that will give playback info
    previousButton = (ImageButton) fragmentView.findViewById(R.id.previous);
    backButton = (ImageButton) fragmentView.findViewById(R.id.back10);
    playButton = (ImageButton) fragmentView.findViewById(R.id.play);
    forwardButton = (ImageButton) fragmentView.findViewById(R.id.forward10);
    nextButton = (ImageButton) fragmentView.findViewById(R.id.next);
    playbackSeekBar = (SeekBar) fragmentView.findViewById(R.id.playback_seek_bar);
    playbackSpeedButton = (ToggleButton) fragmentView.findViewById(R.id.playback_speed_button);

    currentTime = (TextView) fragmentView.findViewById(R.id.position);
    remainingTime = (TextView) fragmentView.findViewById(R.id.remaining);

    // tell the playback buttons the parent activity will be listening to them
    previousButton.setOnClickListener((View.OnClickListener) callbackActivity);
    backButton.setOnClickListener((View.OnClickListener) callbackActivity);
    playButton.setOnClickListener((View.OnClickListener) callbackActivity);
    forwardButton.setOnClickListener((View.OnClickListener) callbackActivity);
    nextButton.setOnClickListener((View.OnClickListener) callbackActivity);
    playbackSeekBar.setOnSeekBarChangeListener((SeekBar.OnSeekBarChangeListener) callbackActivity);
    playbackSpeedButton.setOnClickListener((View.OnClickListener) callbackActivity);

    playButton.setTag(getResources().getString(R.string.play));

  }// initializeControlsUI



  /**
   * Change the message on the download button, letting the user know where we are in the downloading progress.
   */
  public void redrawDownloadButton(String newText) {
    downloadButton.setText(newText);
  }


  /**
   * Change message and pic on play button to match current state.
   *
   * @param newText
   * @param newImageId
   */
  public void redrawPlayButton(String newText, int newImageId) {
    playButton.setImageResource(newImageId);
    playButton.setTag(newText);
  }


  /**
   * Move the seek bar to reflect the current playback position within the book chapter.
   * Making sure the passed event is of type progress update happens in the calling code.
   *
   * @param playbackEvent
   */
  public void redrawPlaybackPosition(PlaybackEvent playbackEvent) {

    playbackSeekBar.setMax((int) playbackEvent.duration);
    playbackSeekBar.setProgress((int) playbackEvent.position);

    currentTime.setText(StringUtils.getTimeString(playbackEvent.position));
    remainingTime.setText(StringUtils.getTimeString(playbackEvent.duration - playbackEvent.position));
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
        currentTime.setText(StringUtils.getTimeString(progress));
      }
    }
  }


  /**
   * Update the progress bar to reflect where we are in the downloading.
   */
  public void redrawDownloadProgress(DownloadEvent downloadEvent) {
    this.redrawDownloadProgress(downloadEvent.contentPercentage, downloadEvent.chapterPercentage);
  }


  public void redrawDownloadProgress(Integer primaryProgress, Integer secondaryProgress) {
    downloadProgress.setProgress(primaryProgress);
    downloadProgress.setSecondaryProgress(secondaryProgress);
    contentPercentage.setText(getString(R.string.contentPercentage, primaryProgress));
    chapterPercentage.setText(getString(R.string.chapterPercentage, secondaryProgress));
  }


  /**
   * Set the display to reflect whether we're reading at increased speed.
   *
   * @param checked
   */
  public void redrawSpeedButton(boolean checked) {
    playbackSpeedButton.setChecked(checked);
  }


  public void resetProgress() {
    this.redrawDownloadProgress(0, 0);
  }

  /* ---------------------------------- /LIFECYCLE METHODS ----------------------------------- */

  /* ------------------------------------ NAVIGATION EVENT HANDLERS ------------------------------------- */

  /* ------------------------------------ /NAVIGATION EVENT HANDLERS ------------------------------------- */



  /* ------------------------------------ PLAYBACK EVENT HANDLERS ------------------------------------- */


  /* ------------------------------------ /PLAYBACK EVENT HANDLERS ------------------------------------- */

}
