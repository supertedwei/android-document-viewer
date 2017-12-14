package de.sitewaerts.cleverdox.viewer;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.*;
import android.widget.SearchView.OnQueryTextListener;
import com.artifex.mupdfdemo.*;

import java.io.*;
import java.lang.reflect.Field;

/**
 * This activity is invoked from cordova plugin to view PDF files
 *
 * @author Philipp Bohnenstengel (raumobil GmbH)
 */
public class DocumentViewerActivity
        extends MuPDFActivity
{
    private static final String TAG = "DocumentViewerActivity";



    private final int SEARCH_FORWARD = 1;
    private final int SEARCH_BACKWARD = -1;

    private View bottomNav;
    private SearchView searchView;

    /*
     * options from cordova
     */
    private String documentViewCloseLabel;
    private String navigationViewCloseLabel;
    private boolean emailEnabled;
    private boolean printEnabled;
    private boolean openWithEnabled;
    private boolean bookmarksEnabled;
    private boolean searchEnabled;
    private boolean autoCloseOnPause;
    private boolean nestedIntentStarted = false;
    private String title;

    private int visiblePages = 1;
    /**
     * remember last search term
     */
    private String cachedSearchTerm = "";

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        nestedIntentStarted = false;
        // Get the intent that started this activity
        Intent intent = getIntent();
        // get all the options passed from cordova
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            Bundle viewerOptions = extras.getBundle(
                    "de.sitewaerts.cordova.documentviewer.DocumentViewerPlugin"); //XXX
            documentViewCloseLabel = viewerOptions
                    .getString("documentView.closeLabel");
            navigationViewCloseLabel = viewerOptions
                    .getString("navigationView.closeLabel");
            emailEnabled = viewerOptions.getBoolean("email.enabled");
            printEnabled = viewerOptions.getBoolean("print.enabled");
            openWithEnabled = viewerOptions.getBoolean("openWith.enabled");
            bookmarksEnabled = viewerOptions.getBoolean("bookmarks.enabled");
            searchEnabled = viewerOptions.getBoolean("search.enabled");
            autoCloseOnPause = viewerOptions.getBoolean("autoClose.onPause");
            title = viewerOptions.getString("title");
        }
        createUI(savedInstanceState);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (autoCloseOnPause && !nestedIntentStarted)
        {
            finish();
        }
    }

    public void startActivityForResult(Intent intent, int requestCode)
    {
        nestedIntentStarted = true;
        super.startActivityForResult(intent, requestCode);
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options)
    {
        nestedIntentStarted = true;
        super.startActivityForResult(intent, requestCode, options);
    }

    public void onDestroy()
    {
        clearTempFiles();
        super.onDestroy();
    }

    private void clearTempFiles()
    {
        File dir = getLocalDir();
        if (!dir.exists())
            return;

        //Log.d(TAG, "clearing temp files below " + dir.getAbsolutePath());
        deleteRecursive(dir, false);
    }

    private void deleteRecursive(File f, boolean self)
    {
        if (!f.exists())
            return;

        if (f.isDirectory())
        {
            File[] files = f.listFiles();
            for (File file : files)
                deleteRecursive(file, true);
        }

        if (self && !f.delete())
            Log.e(TAG, "Failed to delete file " + f.getAbsoluteFile());

    }


    protected Uri resolveContentUri(Uri uri)
   			throws IOException{
        clearTempFiles();
        File f = toLocalFile(uri);
        if(f == null)
            return null;
        return Uri.fromFile(f);
   	}

   	private File getLocalDir(){
   		return new File(getCacheDir(), "local");
   	}

   	private File toLocalFile(Uri uri)
            throws IOException
    {
   	 File local = new File(getLocalDir(), new File(uri.getPath()).getName());
   	 local.deleteOnExit();
   	 try {
   			InputStream is = getContentResolver().openInputStream(uri);
   			copyFile(is, local);
   		}
   		catch(Exception e){
   			 return null;
   		}
   		return local;
    }


   	private void copyFile(InputStream in, File target)
            throws IOException
    {
        OutputStream out = null;
        //create tmp folder if not present
        if (!target.getParentFile().exists() && !target.getParentFile()
                .mkdirs())
            throw new IOException("Cannot create path " + target.getParentFile()
                    .getAbsolutePath()
            );
        try
        {
            out = new FileOutputStream(target);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1)
                out.write(buffer, 0, read);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to copy stream to "
                    + target.getAbsolutePath(), e
            );

   		 throw new IOException(target.getAbsolutePath(), e);
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    // NOOP
                }
            }
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e)
                {
                    // NOOP
                }
            }
        }

    }




    @Override
    public void createUI(Bundle savedInstanceState)
    {
//    	super.createUI(savedInstanceState);
/*
 * XXX this is the method of the super class MuPDFActivity; reimplemented and customized
 */
        if (getCore() == null)
        { //XXX
            return;
        }

        // Now create the UI.
        // First create the document view
        // extension which implements additional features like double tap to zoom
        MuPDFReaderView mDocView = new SDVMuPDFReaderView(this)
        {
            @Override
            protected void onMoveToChild(int i)
            {
                if (getCore() == null)
                    return;
//				TextView mpnv = getMPageNumberView();
//				if (mpnv != null) {
//					mpnv.setText(String.format("%d / %d", i + 1,	getCore().countPages()));
//				}
                updatePageNumView(i);
                SeekBar mps = getMPageSlider();
                if (mps != null)
                {
                    mps.setMax(
                            (getCore().countPages() - 1) * getMPageSliderRes());
                    mps.setProgress(i * getMPageSliderRes());
                }
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea()
            {
//				if (!mButtonsVisible) {
//					showButtons();
//				} else {
//					if (mTopBarMode == TopBarMode.Main)
//						hideButtons();
//				}
                ActionBar ab = getActionBar();
                if (!ab.isShowing())
                {
                    showUI();
                }
                else
                {
                    hideUI();
                }
            }

            @Override
            protected void onDocMotion()
            {
//				hideButtons();
                ActionBar ab = getActionBar();
                if (ab.isShowing())
                {
                    hideUI();
                }
            }

//XXX MuPDF document editing, we don't need that
//			@Override
//			protected void onHit(Hit item) {
//				switch (mTopBarMode) {
//				case Annot:
//					if (item == Hit.Annotation) {
//						showButtons();
//						mTopBarMode = TopBarMode.Delete;
//						mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
//					}
//					break;
//				case Delete:
//					mTopBarMode = TopBarMode.Annot;
//					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
//				// fall through
//				default:
//					// Not in annotation editing mode, but the pageview will
//					// still select and highlight hit annotations, so
//					// deselect just in case.
//					MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
//					if (pageView != null)
//						pageView.deselectAnnotation();
//					break;
//				}
//			}
        };
        mDocView.setAdapter(new MuPDFPageAdapter(this, this, getCore())); //XXX

        SearchTask mSearchTask = new SearchTask(this, getCore())
        { //XXX
            @Override
            protected void onTextFound(SearchTaskResult result)
            {
                SearchTaskResult.set(result);
                MuPDFReaderView mDocView = getMDocView(); //XXX
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };
        setMSearchTask(mSearchTask); //XXX

// XXX replaced by action bar
//		// Make the buttons overlay, and store all its
//		// controls in variables
//		makeButtonsView();

        // Set up the page slider
        int smax = Math.max(getCore().countPages() - 1, 1);
        setMPageSliderRes(((10 + smax - 1) / smax) * 2);

//		// Set the file-name text
//		mFilenameView.setText(mFileName);

// XXX Search is now Actionbar widget
//		// Activate the search-preparing button
//		mSearchButton.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				searchModeOn();
//			}
//		});

// XXX MuPDF reflow mode, we don't need that
//		// Activate the reflow button
//		mReflowButton.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				toggleReflow();
//			}
//		});

// XXX MuPDF document editing, we don't need that
//		if (core.fileFormat().startsWith("PDF"))
//		{
//			mAnnotButton.setOnClickListener(new View.OnClickListener() {
//				public void onClick(View v) {
//					mTopBarMode = TopBarMode.Annot;
//					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
//				}
//			});
//		}
//		else
//		{
//			mAnnotButton.setVisibility(View.GONE);
//		}

// TODO reimplement search
//		// Search invoking buttons are disabled while there is no text specified
//		mSearchBack.setEnabled(false);
//		mSearchFwd.setEnabled(false);
//		mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
//		mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));

//		// React to interaction with the text widget
//		mSearchText.addTextChangedListener(new TextWatcher() {
//
//			public void afterTextChanged(Editable s) {
//				boolean haveText = s.toString().length() > 0;
//				setButtonEnabled(mSearchBack, haveText);
//				setButtonEnabled(mSearchFwd, haveText);
//
//				// Remove any previous search results
//				if (SearchTaskResult.get() != null && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
//					SearchTaskResult.set(null);
//					mDocView.resetupChildren();
//				}
//			}
//			public void beforeTextChanged(CharSequence s, int start, int count,
//					int after) {}
//			public void onTextChanged(CharSequence s, int start, int before,
//					int count) {}
//		});

//		//React to Done button on keyboard
//		mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
//				if (actionId == EditorInfo.IME_ACTION_DONE)
//					search(1);
//				return false;
//			}
//		});

//		mSearchText.setOnKeyListener(new View.OnKeyListener() {
//			public boolean onKey(View v, int keyCode, KeyEvent event) {
//				if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER)
//					search(1);
//				return false;
//			}
//		});

//		// Activate search invoking buttons
//		mSearchBack.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				search(-1);
//			}
//		});
//		mSearchFwd.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				search(1);
//			}
//		});
//
//		mLinkButton.setOnClickListener(new View.OnClickListener() {
//			public void onClick(View v) {
//				setLinkHighlight(!mLinkHighlight);
//			}
//		});

// XXX outline feature is now in action bar menu
//		if (core.hasOutline()) {
//			mOutlineButton.setOnClickListener(new View.OnClickListener() {
//				public void onClick(View v) {
//					OutlineItem outline[] = core.getOutline();
//					if (outline != null) {
//						OutlineActivityData.get().items = outline;
//						Intent intent = new Intent(MuPDFActivity.this, OutlineActivity.class);
//						startActivityForResult(intent, OUTLINE_REQUEST);
//					}
//				}
//			});
//		} else {
//			mOutlineButton.setVisibility(View.GONE);
//		}

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(
                prefs.getInt("page" + getMFileName(), 0)); //XXX

//		if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false))
//			showButtons();
//
//		if(savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
//			searchModeOn();
//
// XXX reflow mode is deactivated
//		if(savedInstanceState != null && savedInstanceState.getBoolean("ReflowMode", false))
//			reflowModeSet(true);

        // Stick the document view and the buttons overlay into a parent view
        RelativeLayout layout = new RelativeLayout(this);
        layout.addView(mDocView);
// XXX buttons view is now action bar
//		layout.addView(mButtonsView);
        //create page slider
        bottomNav = getLayoutInflater()
                .inflate(R.layout.document_bottom_nav, null);
        setMPageSlider((SeekBar) bottomNav.findViewById(R.id.pageSlider));
        setMPageNumberView((TextView) bottomNav.findViewById(R.id.pageNumber));
//		// Activate the seekbar
        getMPageSlider().setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener()
                {
                    public void onStopTrackingTouch(SeekBar seekBar)
                    {
                        getMDocView().setDisplayedViewIndex(
                                (seekBar.getProgress()
                                        + getMPageSliderRes() / 2)
                                        / getMPageSliderRes());
                    }

                    public void onStartTrackingTouch(SeekBar seekBar)
                    {
                    }

                    public void onProgressChanged(SeekBar seekBar, int progress,
                                                  boolean fromUser)
                    {
                        updatePageNumView((progress + getMPageSliderRes() / 2)
                                / getMPageSliderRes());
                    }
                });
        //set initial page slider/pagenum
        int index = mDocView.getDisplayedViewIndex();
        updatePageNumView(index);
        getMPageSlider()
                .setMax((getCore().countPages() - 1) * getMPageSliderRes());
        getMPageSlider().setProgress(index * getMPageSliderRes());


        layout.addView(bottomNav);
        setContentView(layout);

        //add document view
        setMDocView(mDocView);
/*
 * end of MuPDF code
 */
        //create action bar
        ActionBar ab = getActionBar();
        //enable up navigation
        ab.setDisplayHomeAsUpEnabled(true);
//    	//invisible icon
//    	ab.setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
        //set title from cordova options
        if (title != null)
        {
            ab.setTitle(title);
        }
        //TODO make up button wider, disable title clickable

        //restore previous state
        //TODO doesnt work yet
//    	if (savedInstanceState != null) {
//    		if (savedInstanceState.getBoolean("ButtonsHidden")) {
//    			hideUI();
//    		} else {
//    			showUI();
//    		}
//    		if (savedInstanceState.getBoolean("SearchMode")) {
//    			if (searchView != null) {
//    				searchView.setIconified(false);
//    			}
//    		}
//    	}
    }

    private void showUI()
    {
        ActionBar ab = getActionBar();
        ab.show();
        Animation anim = new TranslateAnimation(0, 0, bottomNav.getHeight(), 0);
        anim.setDuration(400);
        anim.setAnimationListener(new Animation.AnimationListener()
        {
            public void onAnimationStart(Animation animation)
            {
                bottomNav.setVisibility(View.VISIBLE);
            }

            public void onAnimationRepeat(Animation animation)
            {
            }

            public void onAnimationEnd(Animation animation)
            {
            }
        });
        bottomNav.startAnimation(anim);
    }

    private void hideUI()
    {
        ActionBar ab = getActionBar();
        ab.hide();
        Animation anim = new TranslateAnimation(0, 0, 0, bottomNav.getHeight());
        anim.setDuration(400);
        anim.setAnimationListener(new Animation.AnimationListener()
        {
            public void onAnimationStart(Animation animation)
            {
                bottomNav.setVisibility(View.INVISIBLE);
            }

            public void onAnimationRepeat(Animation animation)
            {
            }

            public void onAnimationEnd(Animation animation)
            {
            }
        });
        bottomNav.startAnimation(anim);
    }

    private void updatePageNumView(int index)
    {
        if (getCore() == null)
        {
            return;
        }
        TextView mpnv = getMPageNumberView();
        if (mpnv != null)
        {
            mpnv.setText(String.format("%d / %d", index + 1,
                    getCore().countPages()
            ));
        }
    }

    /**
     * create action bar menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.document_view_menu, menu);
        //hide actions based on cordova options
        MenuItem tmp;
        if (getCore() != null)
        {
            if (!getCore().hasOutline() ||
                    //TODO move to NavigationViewActivity once thumbnails are implemented
                    (getCore().countPages() <= visiblePages))
            {
                tmp = menu.findItem(R.id.action_navigation_view);
                if (tmp != null)
                {
//        			tmp.setEnabled(false);
                    tmp.setVisible(false);
                }
            }
        }
        if (!openWithEnabled)
        {
            tmp = menu.findItem(R.id.action_open_with);
            if (tmp != null)
            {
                tmp.setVisible(false);
            }
        }
        if (!printEnabled)
        {
            tmp = menu.findItem(R.id.action_print);
            if (tmp != null)
            {
                tmp.setVisible(false);
            }
        }
        if (!emailEnabled)
        {
            tmp = menu.findItem(R.id.action_email);
            if (tmp != null)
            {
                tmp.setVisible(false);
            }
        }
        if (/*!bookmarksEnabled*/ true)
        { //TODO activate when bookmarks are implemented
            tmp = menu.findItem(R.id.action_bookmark);
            if (tmp != null)
            {
                tmp.setVisible(false);
            }
        }
        if (!searchEnabled)
        {
            tmp = menu.findItem(R.id.action_search);
            if (tmp != null)
            {
                tmp.setVisible(false);
            }
        }
        else
        {
            tmp = menu.findItem(R.id.action_search);
            //configure actionbar search widget
            searchView = (SearchView) tmp.getActionView();
            searchView.setQueryHint(getString(R.string.search_placeholder));
            searchView.setOnQueryTextListener(new OnQueryTextListener()
            {

                public boolean onQueryTextSubmit(String searchTerm)
                {
                    cachedSearchTerm = searchTerm;
                    search(searchTerm, SEARCH_FORWARD);
                    return true;
                }

                public boolean onQueryTextChange(String newText)
                {
                    return false;
                }
            });
            //full width
            searchView.setOnSearchClickListener(new OnClickListener()
            {
                public void onClick(View v)
                {
                    ViewGroup.LayoutParams layoutParams = v.getLayoutParams();
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
            });
            LinearLayout ll = (LinearLayout) searchView.findViewById(
                    getResources()
                            .getIdentifier("android:id/search_plate", null,
                                    null
                            ));
            ImageButton prev = new ImageButton(this);
            // not supported in android API 15
            //prev.setBackground(null);
            prev.setImageDrawable(getResources()
                    .getDrawable(R.drawable.ic_action_previous_item));
            prev.setOnClickListener(new OnClickListener()
            {

                public void onClick(View v)
                {
                    search(cachedSearchTerm, SEARCH_BACKWARD);
                }
            });
            ll.addView(prev);
            ImageButton next = new ImageButton(this);
            // not supported in android API 15
            //next.setBackground(null);
            next.setImageDrawable(
                    getResources().getDrawable(R.drawable.ic_action_next_item));
            next.setOnClickListener(new OnClickListener()
            {

                public void onClick(View v)
                {
                    search(cachedSearchTerm, SEARCH_FORWARD);
                }
            });
            ll.addView(next);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        return true;
        //XXX no call to super, because super class uses fields which were not initialized here
    }

    /**
     * handle action bar menu events
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent myIntent = getIntent();
        Uri docUri = myIntent != null ? myIntent.getData() : null;
        if (docUri != null)
        {
            if (docUri.getScheme() == null)
            {
                docUri = Uri.parse("file://" + docUri.toString());
            }
        }

        // Handle item selection
        int itemId = item.getItemId();
//        switch (item.getItemId())
//        {
            //up navigation
        if (itemId == android.R.id.home) {
//            case android.R.id.home:
            finish();
            return true;
        } else if (itemId == R.id.action_navigation_view) {
//            case R.id.action_navigation_view:
            if (getCore() != null) {
                OutlineItem outline[] = getCore().getOutline();
                if (outline != null) {
                    OutlineActivityData.get().items = outline;
                    Intent intent = new Intent(DocumentViewerActivity.this,
                            NavigationViewActivity.class
                    );
                    //add relevant cordova options
                    intent.putExtra("closeLabel", navigationViewCloseLabel);
                    intent.putExtra("bookmarksEnabled", bookmarksEnabled);
                    startActivityForResult(intent, getOUTLINEREQUEST());
                }
            }
            return true;
        } else if (itemId == R.id.action_open_with) {
//            case R.id.action_open_with:
            Intent openWithIntent = new Intent(Intent.ACTION_VIEW);
            openWithIntent.setDataAndType(docUri,
                    "application/pdf"); //XXX will the app eventually support other document types?
            openWithIntent.setFlags(
                    Intent.FLAG_ACTIVITY_NO_HISTORY); //XXX this is from the example, do we want this behaviour?
            startActivity(Intent.createChooser(openWithIntent,
                    getString(R.string.open_with_chooser_title)
            ));
            return true;
        } else if (itemId == R.id.action_print) {
//            case R.id.action_print:
            if (getCore() != null) {
                if (!getCore().fileFormat().startsWith("PDF")) {
                    showInfo(getString(
                            R.string.format_currently_not_supported));
                    return true;
                }
            }

            Intent printIntent = new Intent(this, PrintActivity.class);
            printIntent.setDataAndType(docUri, "application/pdf");
            printIntent.putExtra("title", getMFileName());
            printIntent.putExtra("closeLabel", navigationViewCloseLabel);
            startActivityForResult(printIntent, getPRINTREQUEST());
            return true;
        } else if (itemId == R.id.action_email) {
//            case R.id.action_email:

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            //XXX tried various methods to filter only email apps
            //filter email apps via mime type, still shows some non email apps + we need mime type for attachment
//	    		emailIntent.setType("message/rfc822");
            //filter email apps via mailto uri, only works with ACTION_VIEW (we need ACTION_SEND)
//	    		Uri data = Uri.parse("mailto:?");
//	    		emailIntent.setData(data);
            //filter email apps via category, only works with ACTION_MAIN (we need ACTION_SEND) and launches gmail without showing chooser first
//	    		emailIntent.addCategory(Intent.CATEGORY_APP_EMAIL);
            //TODO research how other people do this (e.g. phonegap share plugin)

            //add document as attachment
            emailIntent.setType("application/pdf");
            emailIntent.putExtra(Intent.EXTRA_STREAM, docUri);
            //add some text to the email
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            emailIntent.putExtra(Intent.EXTRA_TEXT,
                    String.format(getString(R.string.email_text),
                            getString(R.string.app_name)
                    )
            );
            //choose email app
            startActivity(Intent.createChooser(emailIntent,
                    getString(R.string.email_chooser_title)
            ));
            return true;
        } else if (itemId == R.id.action_bookmark) {
//            case R.id.action_bookmark:
            //TODO
            return false;
        } else {
//            default:
            return super.onOptionsItemSelected(item);
        }
//        }
    }

    public void finishActivity(int requestCode) {
        nestedIntentStarted = false;
        super.finishActivity(requestCode);
    }

    public void finishActivityFromChild(Activity child, int requestCode) {
        nestedIntentStarted = false;
        super.finishActivityFromChild(child, requestCode);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        nestedIntentStarted = false;
        if (requestCode == getPRINTREQUEST())
        {
            if (resultCode == RESULT_CANCELED)
            {
                showInfo(getString(R.string.print_failed));
            }
            return; //prevent call to super
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showInfo(String message)
    {
        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        // reuse keys defined by MuPDF
        if (!getActionBar().isShowing())
        {
            outState.putBoolean("ButtonsHidden", true);
        }

        if (searchView != null)
        {
            if (!searchView.isIconified())
            {
                outState.putBoolean("SearchMode", true);
            }
        }
    }

    private void search(String searchTerm, int direction)
    {
        SearchTask st = getMSearchTask();
        int displayPage = getMDocView().getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        st.go(searchTerm, direction, displayPage, searchPage);
    }

    /**
     * XXX here be dragons
     * using reflection to be able to extend MuPDF code with its private fields everywhere...
     *
     * @param fieldname
     * @return value of requested field (has to be cast to field class or null if field does not exist
     */
    private Object getPrivateFieldOfSuper(String fieldname)
    {
        Class<?> mpa = this.getClass().getSuperclass();
        try
        {
            Field f = mpa.getDeclaredField(fieldname);
            f.setAccessible(true);
            return f.get(this);
        }
        catch (NoSuchFieldException e)
        {
            return null;
        }
        catch (IllegalAccessException e)
        {
            return null;
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    /**
     * XXX here be dragons
     * using reflection to be able to extend MuPDF code with its private fields everywhere...
     *
     * @param fieldname
     * @param value     new value
     * @return true if successful else false
     */
    private boolean setPrivateFieldOfSuper(String fieldname, Object value)
    {
        Class<?> mpa = this.getClass().getSuperclass();
        try
        {
            Field f = mpa.getDeclaredField(fieldname);
            f.setAccessible(true);
            f.set(this, value);
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
        catch (IllegalAccessException e)
        {
            return false;
        }
        catch (IllegalArgumentException e)
        {
            return false;
        }
    }

    public MuPDFReaderView getMDocView()
    {
        return (MuPDFReaderView) getPrivateFieldOfSuper("mDocView");
    }

    public boolean setMDocView(MuPDFReaderView mprv)
    {
        return setPrivateFieldOfSuper("mDocView", mprv);
    }

    public SeekBar getMPageSlider()
    {
        return (SeekBar) getPrivateFieldOfSuper("mPageSlider");
    }

    public boolean setMPageSlider(SeekBar mps)
    {
        return setPrivateFieldOfSuper("mPageSlider", mps);
    }

    public int getMPageSliderRes()
    {
        return (Integer) getPrivateFieldOfSuper("mPageSliderRes");
    }

    public boolean setMPageSliderRes(int mpsr)
    {
        return setPrivateFieldOfSuper("mPageSliderRes", mpsr);
    }

    public TextView getMPageNumberView()
    {
        return (TextView) getPrivateFieldOfSuper("mPageNumberView");
    }

    public boolean setMPageNumberView(TextView mpnv)
    {
        return setPrivateFieldOfSuper("mPageNumberView", mpnv);
    }

    public MuPDFCore getCore()
    {
        return (MuPDFCore) getPrivateFieldOfSuper("core");
    }

    public SearchTask getMSearchTask()
    {
        return (SearchTask) getPrivateFieldOfSuper("mSearchTask");
    }

    public boolean setMSearchTask(SearchTask st)
    {
        return setPrivateFieldOfSuper("mSearchTask", st);
    }

    public String getMFileName()
    {
        return (String) getPrivateFieldOfSuper("mFileName");
    }

    public int getOUTLINEREQUEST()
    {
        return (Integer) getPrivateFieldOfSuper("OUTLINE_REQUEST");
    }

    public int getPRINTREQUEST()
    {
        return (Integer) getPrivateFieldOfSuper("PRINT_REQUEST");
    }
}
