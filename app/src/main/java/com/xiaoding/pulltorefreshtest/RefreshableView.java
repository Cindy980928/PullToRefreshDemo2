package com.xiaoding.pulltorefreshtest;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class RefreshableView extends LinearLayout implements View.OnTouchListener {

    public static final int STATUS_PULL_TO_REFRESH = 0;//下拉状态
    public static final int STATUS_RELEASE_TO_REFRESH = 1;//释放立即刷新状态
    public static final int STATUS_REFRESHING = 2;//正在刷新状态
    public static final int STATUS_REFRESH_FINISHED = 3;//刷新结束或未刷新状态
    public static final int SCROLL_SPEED = -20;//下拉头部回滚的速度
    private int currentStatus = STATUS_REFRESH_FINISHED;
    private int lastStatus = currentStatus;
    private View header;
    private ImageView allow;
    private ProgressBar progressBar;
    private TextView descriptionTextView;
    private TextView updatedTimeView;
    private MarginLayoutParams headerLayoutParams;//下拉头的布局参数
    private PullToRefreshListener mListener;//下拉刷新的回调接口
    private int mId = -1;//
    private int distance;
    private int hideHeaderHeight;
    private int toushSlop;//滑动事件最小距离
    private boolean ableToPull;//是否允许下拉
    private float yDown;//手指按下时屏幕的坐标
    private ListView listView;

    private SharedPreferences preferences;

    public RefreshableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = context.getSharedPreferences("update_at" + mId, Context.MODE_PRIVATE);
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        header = layoutInflater.inflate(R.layout.pull_to_refresh, null);
        allow = (ImageView) header.findViewById(R.id.arrow);
        progressBar = (ProgressBar) header.findViewById(R.id.progress_bar);
        descriptionTextView = (TextView) header.findViewById(R.id.description);
        updatedTimeView = (TextView) header.findViewById(R.id.update_at);

        toushSlop = ViewConfiguration.get(context).getScaledTouchSlop();//触发移动事件的最小距离
        addView(header);
        setOrientation(VERTICAL);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        setIsAbleToPull(event);
        if (ableToPull) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    Log.d("RefreshableView", "按下");
                    break;
                case MotionEvent.ACTION_MOVE:
                    float yMove = event.getRawY();
                    distance = (int) (yMove - yDown);
                    Log.d("refreshableview", "滑动了" + String.valueOf(distance));
                    //下滑且下拉头完全隐藏
                    if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight)
                        return false;
                    if (distance < toushSlop)//滑动距离小于触发滑动事件的最小距离
                        return false;
                    if (currentStatus != STATUS_REFRESHING) {
                        if (headerLayoutParams.topMargin > 0) {
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        } else {
                            currentStatus = STATUS_PULL_TO_REFRESH;
                        }
                        updateHeaderView();
                        //下拉效果
                        headerLayoutParams.topMargin = (int) (hideHeaderHeight + (distance * 0.5));
                        header.setLayoutParams(headerLayoutParams);
                    }
                    break;
                //松手时
                case MotionEvent.ACTION_UP:
                default:
                    Log.d("refreshableview", "松手" + currentStatus);
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                        refreshAnimation();
                    } else if (currentStatus == STATUS_PULL_TO_REFRESH) {
                        finishAnimation();
                    }
                    break;
            }
            if (currentStatus == STATUS_PULL_TO_REFRESH || currentStatus == STATUS_RELEASE_TO_REFRESH) {
                lastStatus = currentStatus;
                return true;
            }
            updateHeaderView();

        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (changed && getChildCount() > 0) {
            hideHeaderHeight = -header.getHeight();
            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            headerLayoutParams.topMargin = hideHeaderHeight;
            header.setLayoutParams(headerLayoutParams);
            Log.d("refreshableview", String.valueOf(header.getHeight()));
            listView = (ListView) getChildAt(1);
            listView.setOnTouchListener(this);
        }
    }

    private void setIsAbleToPull(MotionEvent event) {
        //MotionEvent类存储手指移动状态
        View firstChild = listView.getChildAt(0);
        if (firstChild != null) {
            int firstVisiblePos = listView.getFirstVisiblePosition();//listview中当前可视的第一个item的位置

            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {//firstChild.getTop()是firstChild距离listview顶部的距离
                if (!ableToPull)
                    yDown = event.getRawY();//按下点至屏幕顶部的距离
                ableToPull = true;
            } else {
                ableToPull = false;
            }

        } else {
            //listView为空，也允许刷新
            ableToPull = true;
        }
    }

    private void updateHeaderView() {
        if (lastStatus != currentStatus) {
            if (currentStatus == STATUS_PULL_TO_REFRESH) {
                descriptionTextView.setText(getResources().getString(R.string.pull_to_refresh));
                allow.setVisibility(View.VISIBLE);
                allow.setImageResource(R.drawable.down);
                progressBar.setVisibility(View.GONE);
                allow.setImageResource(R.drawable.down);
            } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                descriptionTextView.setText(getResources().getString(R.string.release_to_refresh));
                allow.setVisibility(View.VISIBLE);
                allow.setImageResource(R.drawable.up);
                progressBar.setVisibility(View.GONE);
                allow.setImageResource(R.drawable.up);
            } else if (currentStatus == STATUS_REFRESHING) {
                descriptionTextView.setText(getResources().getString(R.string.refreshing));
                progressBar.setVisibility(View.VISIBLE);
                allow.setVisibility(View.GONE);
            }
            long updatedTime = preferences.getLong("update_at" + mId, -1);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            Date date = new Date(updatedTime);
            String time = formatter.format(date);
            updatedTimeView.setText("上次更新于" + time);
        }
    }

    public void setOnRefreshListener(PullToRefreshListener listener, int id) {
        mListener = listener;
        mId = id;
    }

    private void refreshAnimation(){
        currentStatus=STATUS_REFRESHING;
        updateHeaderView();
        lastStatus=currentStatus;
        int curMargin=headerLayoutParams.topMargin;
        ValueAnimator animator=ValueAnimator.ofInt(curMargin,0);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                Log.d("value",valueAnimator.getAnimatedValue()+"");
                headerLayoutParams.topMargin= (int) valueAnimator.getAnimatedValue();
                header.setLayoutParams(headerLayoutParams);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mListener != null) {
                    mListener.onRefresh();
                }
            }
        });
        animator.setDuration(500);
        animator.start();
    }

    public void finishAnimation(){
        if(currentStatus==STATUS_REFRESH_FINISHED)
            return;
        currentStatus = STATUS_REFRESH_FINISHED;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("update_at" + mId, System.currentTimeMillis()).commit();
        int curMargin=headerLayoutParams.topMargin;
        ValueAnimator animator=ValueAnimator.ofInt(curMargin,hideHeaderHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                headerLayoutParams.topMargin= (int) valueAnimator.getAnimatedValue();
                header.setLayoutParams(headerLayoutParams);
            }
        });
        animator.setDuration(500);
        animator.start();
    }

    public interface PullToRefreshListener {

        void onRefresh();

    }

}

