package com.github.btlebeacontracker.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;


public class CheckableRelativeLayout extends RelativeLayout implements Checkable {

    private boolean isChecked;
    private List<Checkable> checkableViews;
    private OnCheckedChangeListener onCheckedChangeListener;

    public CheckableRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialise();
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialise();
    }

    public CheckableRelativeLayout(Context context) {
        super(context);
        initialise();
    }

    /*
     * @see android.widget.Checkable#isChecked()
     */
    public boolean isChecked() {
        return isChecked;
    }

    /*
     * @see android.widget.Checkable#setChecked(boolean)
     */
    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
        for (Checkable c : checkableViews) {
            c.setChecked(isChecked);
        }

        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, isChecked);
        }
    }

    /*
     * @see android.widget.Checkable#toggle()
     */
    public void toggle() {
        this.isChecked = !this.isChecked;
        for (Checkable c : checkableViews) {
            c.toggle();
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener onCheckedChangeListener) {
        this.onCheckedChangeListener = onCheckedChangeListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final int childCount = this.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            findCheckableChildren(this.getChildAt(i));
        }
    }

    /**
     * Read the custom XML attributes
     */
    private void initialise() {
        this.isChecked = false;
        this.checkableViews = new ArrayList<>(5);
    }

    /**
     * Add to our checkable list all the children of the view that implement the interface Checkable
     */
    private void findCheckableChildren(View v) {
        if (v instanceof Checkable) {
            this.checkableViews.add((Checkable) v);
        }

        if (v instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) v;
            final int childCount = vg.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                findCheckableChildren(vg.getChildAt(i));
            }
        }
    }

    /**
     * Interface definition for a callback to be invoked when the checked state of a CheckableRelativeLayout changed.
     */
    interface OnCheckedChangeListener {
        void onCheckedChanged(CheckableRelativeLayout layout, boolean isChecked);
    }
}
