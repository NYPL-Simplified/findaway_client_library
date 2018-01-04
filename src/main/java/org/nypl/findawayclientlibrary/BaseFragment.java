package org.nypl.findawayclientlibrary;

import android.app.Activity;
import android.support.v4.app.Fragment;
//import android.support.v4.media.session.MediaControllerCompat;


/**
 * Contains top-level functionality, s.a. knowing how to load fragments into a parent frame.
 *
 * Created by daryachernikhova on 7/21/17.
 */
public class BaseFragment extends Fragment {
  // so can filter all log msgs belonging to my app
  public static final String APP_TAG = "FSLIB.";

  // so can do a search in log msgs for just this class's output
  private final String TAG = APP_TAG + "BaseFragment";

  public ReplaceFragmentListener callbackActivity;
  // The container Activity must implement this interface so the fragment can deliver messages.
  public interface ReplaceFragmentListener {
    /** Called by child fragments when the next fragment is to be loaded into the view. */
    public void onBookSelected(String demoBookId);

    //public MediaControllerCompat getController();
  }


  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);

    // This makes sure that the container activity has implemented
    // the callback interface. If not, it throws an exception.
    try {
      callbackActivity = (ReplaceFragmentListener) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString() + " must implement ReplaceFragmentListener");
    }
  }


}
