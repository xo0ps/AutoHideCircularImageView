package info.msadeghi.autohidecircularimageview.widget;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import info.msadeghi.autohidecircularimageview.R;

/**
 * Created by mahdi on 8/18/16.
 */
@CoordinatorLayout.DefaultBehavior(AutoHideCircularImageView.Behavior.class)
public class AutoHideCircularImageView extends CircleImageView {

    private int mUserSetVisibility;
    private boolean mIsHiding;
    static final int SHOW_HIDE_ANIM_DURATION = 200;
    private WindowInsetsCompat mLastInsets;

    public AutoHideCircularImageView(Context context) {
        this(context, null);
    }

    public AutoHideCircularImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoHideCircularImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mUserSetVisibility = getVisibility();
    }

    @Override
    public void setVisibility(int visibility) {
        internalSetVisibility(visibility, true);
    }

    final void internalSetVisibility(int visibility, boolean fromUser) {
        super.setVisibility(visibility);
        if (fromUser) {
            mUserSetVisibility = visibility;
        }
    }

    final int getUserSetVisibility() {
        return mUserSetVisibility;
    }

    public abstract static class OnVisibilityChangedListener {

        public void onShown(AutoHideCircularImageView fab) {}
        public void onHidden(AutoHideCircularImageView fab) {}
    }

    public void show() {
        show(null);
    }

    public void show(@Nullable final OnVisibilityChangedListener listener) {
        show(listener, true);
    }

    private void show(final OnVisibilityChangedListener listener, boolean fromUser) {
        if (getVisibility() != View.VISIBLE || mIsHiding) {
            clearAnimation();
            internalSetVisibility(View.VISIBLE, fromUser);
            Animation anim = android.view.animation.AnimationUtils.loadAnimation(
                    getContext(), R.anim.show_in);
            anim.setDuration(SHOW_HIDE_ANIM_DURATION);
            anim.setInterpolator(AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR);
            anim.setAnimationListener(new AnimationUtils.AnimationListenerAdapter() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    if (listener != null) {
                        listener.onShown(AutoHideCircularImageView.this);
                    }
                }
            });
            startAnimation(anim);
        } else {
            if (listener != null) {
                listener.onShown(this);
            }
        }
    }

    public void hide() {
        hide(null);
    }

    public void hide(@Nullable OnVisibilityChangedListener listener) {
        hide(listener, true);
    }

    private void hide(@Nullable final OnVisibilityChangedListener listener, final boolean fromUser) {
        if (mIsHiding || getVisibility() != View.VISIBLE) {
            // A hide animation is in progress, or we're already hidden. Skip the call
            if (listener != null) {
                listener.onHidden(this);
            }
            return;
        }

        Animation anim = android.view.animation.AnimationUtils.loadAnimation(
                getContext(), R.anim.hide_out);
        anim.setInterpolator(AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR);
        anim.setDuration(SHOW_HIDE_ANIM_DURATION);
        anim.setAnimationListener(new AnimationUtils.AnimationListenerAdapter() {
            @Override
            public void onAnimationStart(Animation animation) {
                mIsHiding = true;
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mIsHiding = false;
                internalSetVisibility(View.GONE, fromUser);
                if (listener != null) {
                    listener.onHidden(AutoHideCircularImageView.this);
                }
            }
        });
        startAnimation(anim);
    }
    public static class Behavior extends CoordinatorLayout.Behavior<AutoHideCircularImageView> {
        // We only support the FAB <> Snackbar shift movement on Honeycomb and above. This is
        // because we can use view translation properties which greatly simplifies the code.
        private static final boolean SNACKBAR_BEHAVIOR_ENABLED = Build.VERSION.SDK_INT >= 11;

        private ValueAnimatorCompat mFabTranslationYAnimator;
        private float mFabTranslationY;
        private Rect mTmpRect;

        public Behavior() {
            super();
        }

        public Behavior(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean layoutDependsOn(CoordinatorLayout parent,
                                       AutoHideCircularImageView child, View dependency) {
            // We're dependent on all SnackbarLayouts (if enabled)
            return SNACKBAR_BEHAVIOR_ENABLED && dependency instanceof Snackbar.SnackbarLayout;
        }

        @Override
        public boolean onDependentViewChanged(CoordinatorLayout parent, AutoHideCircularImageView child,
                                              View dependency) {
            if (dependency instanceof Snackbar.SnackbarLayout) {
                updateFabTranslationForSnackbar(parent, child, true);
            } else if (dependency instanceof AppBarLayout) {
                // If we're depending on an AppBarLayout we will show/hide it automatically
                // if the FAB is anchored to the AppBarLayout
                updateFabVisibility(parent, (AppBarLayout) dependency, child);
            }
            return false;
        }

        @Override
        public void onDependentViewRemoved(CoordinatorLayout parent, AutoHideCircularImageView child,
                                           View dependency) {
            if (dependency instanceof Snackbar.SnackbarLayout) {
                updateFabTranslationForSnackbar(parent, child, true);
            }
        }

        private boolean updateFabVisibility(CoordinatorLayout parent,
                                            AppBarLayout appBarLayout, AutoHideCircularImageView child) {
            final CoordinatorLayout.LayoutParams lp =
                    (CoordinatorLayout.LayoutParams) child.getLayoutParams();
            if (lp.getAnchorId() != appBarLayout.getId()) {
                // The anchor ID doesn't match the dependency, so we won't automatically
                // show/hide the FAB
                return false;
            }

            if (child.getUserSetVisibility() != VISIBLE) {
                // The view isn't set to be visible so skip changing its visibility
                return false;
            }

            if (mTmpRect == null) {
                mTmpRect = new Rect();
            }

            // First, let's get the visible rect of the dependency
            final Rect rect = mTmpRect;
            ViewGroupUtils.getDescendantRect(parent, appBarLayout, rect);

            if (rect.bottom <= getMinimumHeightForVisibleOverlappingContent(appBarLayout)) {
                // If the anchor's bottom is below the seam, we'll animate our FAB out
                child.hide(null, false);
            } else {
                // Else, we'll animate our FAB back in
                child.show(null, false);
            }
            return true;
        }

        final int getMinimumHeightForVisibleOverlappingContent(AppBarLayout appBarLayout) {
            final int topInset = getTopInset();
            final int minHeight = ViewCompat.getMinimumHeight(appBarLayout);
            if (minHeight != 0) {
                // If this layout has a min height, use it (doubled)
                return (minHeight * 2) + topInset;
            }

            // Otherwise, we'll use twice the min height of our last child
            final int childCount = appBarLayout.getChildCount();
            final int lastChildMinHeight = childCount >= 1
                    ? ViewCompat.getMinimumHeight(appBarLayout.getChildAt(childCount - 1)) : 0;
            if (lastChildMinHeight != 0) {
                return (lastChildMinHeight * 2) + topInset;
            }

            // If we reach here then we don't have a min height explicitly set. Instead we'll take a
            // guess at 1/3 of our height being visible
            return appBarLayout.getHeight() / 3;
        }

        @VisibleForTesting
        final int getTopInset() {
            //return mLastInsets != null ? mLastInsets.getSystemWindowInsetTop() : 0;
            return 0;
        }

        private void updateFabTranslationForSnackbar(CoordinatorLayout parent,
                                                     final AutoHideCircularImageView fab, boolean animationAllowed) {
            final float targetTransY = getFabTranslationYForSnackbar(parent, fab);
            if (mFabTranslationY == targetTransY) {
                // We're already at (or currently animating to) the target value, return...
                return;
            }

            final float currentTransY = ViewCompat.getTranslationY(fab);

            // Make sure that any current animation is cancelled
            if (mFabTranslationYAnimator != null && mFabTranslationYAnimator.isRunning()) {
                mFabTranslationYAnimator.cancel();
            }

            if (animationAllowed && fab.isShown()
                    && Math.abs(currentTransY - targetTransY) > (fab.getHeight() * 0.667f)) {
                // If the FAB will be travelling by more than 2/3 of its height, let's animate
                // it instead
                if (mFabTranslationYAnimator == null) {
                    mFabTranslationYAnimator = ViewUtils.createAnimator();
                    mFabTranslationYAnimator.setInterpolator(
                            AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
                    mFabTranslationYAnimator.setUpdateListener(
                            new ValueAnimatorCompat.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimatorCompat animator) {
                                    ViewCompat.setTranslationY(fab,
                                            animator.getAnimatedFloatValue());
                                }
                            });
                }
                mFabTranslationYAnimator.setFloatValues(currentTransY, targetTransY);
                mFabTranslationYAnimator.start();
            } else {
                // Now update the translation Y
                ViewCompat.setTranslationY(fab, targetTransY);
            }

            mFabTranslationY = targetTransY;
        }

        private float getFabTranslationYForSnackbar(CoordinatorLayout parent,
                                                    AutoHideCircularImageView fab) {
            float minOffset = 0;
            final List<View> dependencies = parent.getDependencies(fab);
            for (int i = 0, z = dependencies.size(); i < z; i++) {
                final View view = dependencies.get(i);
                if (view instanceof Snackbar.SnackbarLayout && parent.doViewsOverlap(fab, view)) {
                    minOffset = Math.min(minOffset,
                            ViewCompat.getTranslationY(view) - view.getHeight());
                }
            }

            return minOffset;
        }

        @Override
        public boolean onLayoutChild(CoordinatorLayout parent, AutoHideCircularImageView child,
                                     int layoutDirection) {
            // First, let's make sure that the visibility of the FAB is consistent
            final List<View> dependencies = parent.getDependencies(child);
            for (int i = 0, count = dependencies.size(); i < count; i++) {
                final View dependency = dependencies.get(i);
                if (dependency instanceof AppBarLayout
                        && updateFabVisibility(parent, (AppBarLayout) dependency, child)) {
                    break;
                }
            }
            // Now let the CoordinatorLayout lay out the FAB
            parent.onLayoutChild(child, layoutDirection);
            // Now offset it if needed
            offsetIfNeeded(parent, child);
            // Make sure we translate the FAB for any displayed Snackbars (without an animation)
            updateFabTranslationForSnackbar(parent, child, false);
            return true;
        }

        /**
         * Pre-Lollipop we use padding so that the shadow has enough space to be drawn. This method
         * offsets our layout position so that we're positioned correctly if we're on one of
         * our parent's edges.
         */
        private void offsetIfNeeded(CoordinatorLayout parent, AutoHideCircularImageView fab) {
            final Rect padding = new Rect();

            if (padding.centerX() > 0 && padding.centerY() > 0) {
                final CoordinatorLayout.LayoutParams lp =
                        (CoordinatorLayout.LayoutParams) fab.getLayoutParams();

                int offsetTB = 0, offsetLR = 0;

                if (fab.getRight() >= parent.getWidth() - lp.rightMargin) {
                    // If we're on the left edge, shift it the right
                    offsetLR = padding.right;
                } else if (fab.getLeft() <= lp.leftMargin) {
                    // If we're on the left edge, shift it the left
                    offsetLR = -padding.left;
                }
                if (fab.getBottom() >= parent.getBottom() - lp.bottomMargin) {
                    // If we're on the bottom edge, shift it down
                    offsetTB = padding.bottom;
                } else if (fab.getTop() <= lp.topMargin) {
                    // If we're on the top edge, shift it up
                    offsetTB = -padding.top;
                }

                fab.offsetTopAndBottom(offsetTB);
                fab.offsetLeftAndRight(offsetLR);
            }
        }
    }
}
