package com.tokbox.android.opentokrtc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;

public class Room extends Session {
    public static final String TAG = "Room";

	Context mContext;
	String apikey;
	String sessionId;
	String token;
	
	String mPublisherName = "name";
	
	// Interface
	ViewPager mParticipantsViewContainer;
	ViewGroup mPreview;
	TextView mMessageView;
	ScrollView mMessageScroll;
	OnClickListener onSubscriberUIClick;
	
	protected Publisher mPublisher;
	protected Participant mCurrentParticipant;

	// Players status
	ArrayList<Participant> mParticipants = new ArrayList<Participant>();
	HashMap<Stream, Participant> mParticipantStream = new HashMap<Stream, Participant>();
	HashMap<String, Participant> mParticipantConnection = new HashMap<String, Participant>();

	PagerAdapter mPagerAdapter = new PagerAdapter() {

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return ((Participant) arg1).getView() == arg0;
		}

		@Override
		public int getCount() {
			return mParticipants.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			if (position < mParticipants.size()) {
				return mParticipants.get(position).getName();
			} else {
				return null;
			}
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			Participant p = mParticipants.get(position);
			container.addView(p.getView());
			return p;
		}

		@Override
		public void setPrimaryItem(ViewGroup container, int position,
				Object object) {
			for (Participant p : mParticipants) {
				if (p == object) {
					mCurrentParticipant = p;
					if (!p.getSubscribeToVideo()) {
						p.setSubscribeToVideo(true);
					}
				} else {
					if (p.getSubscribeToVideo()) {
						p.setSubscribeToVideo(false);
					}
				}
			}
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			Participant p = (Participant) object;
			container.removeView(p.getView());
		}

		@Override
		public int getItemPosition(Object object) {
			for (int i = 0; i < mParticipants.size(); i++) {
				if (mParticipants.get(i) == object) {
					return i;
				}
			}
			return POSITION_NONE;
		}

	};

	public Room(Context context, String roomName, String sessionId, String token, String apiKey) {
		super(context, apiKey, sessionId, null);
		this.apikey = apiKey;
		this.sessionId = sessionId;
		this.token = token;
		this.mContext = context;
		this.mHandler = new Handler(context.getMainLooper());
	}

	// public methods
	public void setPlayersViewContainer(ViewPager container, OnClickListener onSubscriberUIClick) {
        this.mParticipantsViewContainer = container;
        this.mParticipantsViewContainer.setAdapter(mPagerAdapter);
        this.onSubscriberUIClick = onSubscriberUIClick;
        mPagerAdapter.notifyDataSetChanged();
    }

	public void setMessageView(TextView et, ScrollView scroller) {
		this.mMessageView = et;
		this.mMessageScroll = scroller;
	}

	public void setPreviewView(ViewGroup preview) {
		this.mPreview = preview;
	}

	public void connect() {
		this.connect(token);
	}

	public void sendChatMessage(String message) {
	    JSONObject json = new JSONObject();
	    try {
            json.put("name", mPublisherName);
            json.put("text", message);
            sendSignal("chat", json.toString());
            presentMessage("Me", message);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }	    
	}

	// callbacks
	@Override
	protected void onConnected(Session session) {
		Publisher p = new Publisher(mContext, null, "Android");
		mPublisher = p;
		publish(p);

		// Add video preview
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		SurfaceView v = (SurfaceView)p.getView();
		v.setZOrderOnTop(true);
        
		mPreview.addView(v, lp);
		p.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
				BaseVideoRenderer.STYLE_VIDEO_FILL);

		presentText("Welcome to OpenTokRTC by TokBox.");
	}

	@Override
	protected void onReceivedStream(Session session, Stream stream) {
		Participant p = new Participant(mContext, stream);
	
		// we can use connection data to obtain each user id
		p.setUserId(stream.getConnection().getData());

		// Subscribe audio only if we have more than one player
		if (mParticipants.size() != 0) {
			p.setSubscribeToVideo(false);
		}

		p.getView().setOnClickListener(this.onSubscriberUIClick);
		
		// Subscribe to this player
		this.subscribe(p);

		mParticipants.add(p);
		mParticipantStream.put(stream, p);
		mParticipantConnection.put(stream.getConnection().getConnectionId(), p);
		mPagerAdapter.notifyDataSetChanged();

		presentText("\n" + p.getName() + " has joined the chat");
	}

	@Override
	protected void onDroppedStream(Session session, Stream stream) {
		Participant p = mParticipantStream.get(stream);
		if (p != null) {
			mParticipants.remove(p);
			mParticipantStream.remove(stream);
			mParticipantConnection.remove(stream.getConnection().getConnectionId());
			mPagerAdapter.notifyDataSetChanged();

			presentText("\n" + p.getName() + " has left the chat");
		}
	}

	@Override
	protected void onSignal(Session session, String type, String data,
			Connection connection) {
	    Log.d(TAG, "Received signal:" + type + " data:" + data);
        String mycid = this.getConnection().getConnectionId();
        String cid = connection.getConnectionId();
        if (!cid.equals(mycid)) {
            if ("chat".equals(type)) {
                // Text message
                Participant p = mParticipantConnection.get(cid);
                if (p != null) {
                    JSONObject json;
                    try {
                        json = new JSONObject(data);
                        String text = json.getString("text");
                        String name = json.getString("name");
                        p.setName(name);
                        presentMessage(p.getName(), text);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else if("name".equals(type)) {
                // Name change message
                Participant p = mParticipantConnection.get(cid);
                if (p != null) {
                    try {
                        String oldName = p.getName();
                        JSONArray jsonArray = new JSONArray(data);
                        String name = jsonArray.getString(1);
                        p.setName(name);
                        presentText("\n" + oldName + " is now known as " + name);
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else if("initialize".equals(type)) {
                // Initialize message
                try {
                    JSONObject json = new JSONObject(data);
                    JSONObject users = json.getJSONObject("users");
                    Iterator<String> it = users.keys();
                    while(it.hasNext()) {
                        String pcid = it.next();
                        Participant p = mParticipantConnection.get(pcid);
                        if (p != null) {
                            p.setName(users.getString(pcid));
                        }
                    }
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                Participant p = mParticipantConnection.get(cid);
                if (p != null) {
                }
            }
        }
	}

	private void presentMessage(String who, String message) {
		presentText("\n" + who + ": " + message);
	}

	private void presentText(String message) {
		mMessageView.setText(mMessageView.getText() + message);
		mMessageScroll.post(new Runnable() {
			@Override
			public void run() {
				int totalHeight = mMessageView.getHeight();
				mMessageScroll.smoothScrollTo(0, totalHeight);
			}
		});
	}
	
	public Publisher getmPublisher() {
		return mPublisher;
	}

	public Participant getmCurrentParticipant() {
		return mCurrentParticipant;
	}

    @Override
    public void onPause() {
        super.onPause();
        if(mPublisher != null) {
            mPreview.setVisibility(View.GONE);
        }
    }

    Handler mHandler;
    @Override
    public void onResume() {
        super.onResume();
        mHandler.postDelayed(new Runnable() {
            
            @Override
            public void run() {
                if(mPublisher != null) {
                    mPreview.setVisibility(View.VISIBLE);
                    mPreview.removeView(mPublisher.getView());
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                    mPreview.addView(mPublisher.getView(), lp);
                }
            }
        }, 500);
    }

	
}