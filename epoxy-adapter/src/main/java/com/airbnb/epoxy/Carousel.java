package com.airbnb.epoxy;

import android.content.Context;
import android.support.annotation.DimenRes;
import android.support.annotation.Dimension;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.epoxy.ModelView.Size;
import com.airbnb.viewmodeladapter.R;

import java.util.List;

/**
 * <i>This feature is in Beta - please report bugs, feature requests, or other feedback at
 * https://github.com/airbnb/epoxy by creating a new issue. Thanks!</i>
 * <p>
 * This is intended as a plug and play "Carousel" view - a Recyclerview with horizontal scrolling.
 * It comes with common defaults and performance optimizations and can be either used as a top level
 * RecyclerView, or nested within a vertical recyclerview.
 * <p>
 * This class provides:
 * <p>
 * 1. Automatic integration with Epoxy. A {@link CarouselModel_} is generated from this class, which
 * you can use in your EpoxyController. Just call {@link #setModels(List)} to provide the list of
 * models to show in the carousel.
 * <p>
 * 2. Default horizontal padding for carousel peeking, and an easy way to change this padding -
 * {@link #setCarouselPadding(int)}
 * <p>
 * 3. Easily control how many items are shown on screen in the carousel at a time - {@link
 * #setNumViewsToShowOnScreen(float)}
 * <p>
 * 4. All of the benefits of {@link EpoxyRecyclerView}
 * <p>
 * If you need further flexibility you can subclass this view to change its width, height, scrolling
 * direction, etc. You can annotate a subclass with {@link ModelView} to generate a new EpoxyModel.
 */
@ModelView(saveViewState = true, autoLayout = Size.MATCH_WIDTH_WRAP_HEIGHT)
public class Carousel extends EpoxyRecyclerView {


  private float numViewsToShowOnScreen;

  public Carousel(Context context) {
    super(context);
    new LinearSnapHelper().attachToRecyclerView(this);

  }

  public Carousel(Context context,
      @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public Carousel(Context context, @Nullable AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @ModelProp
  @Override
  public void setHasFixedSize(boolean hasFixedSize) {
    super.setHasFixedSize(hasFixedSize);
  }

  /**
   * Set the number of views to show on screen in this carousel at a time, partial numbers are
   * allowed.
   * <p>
   * This is useful where you want to easily control for the number of items on screen, regardless
   * of screen size. For example, you could set this to 1.2f so that one view is shown in full and
   * 20% of the next view "peeks" from the edge to indicate that there is more content to scroll
   * to.
   * <p>
   * Another pattern is setting a different view count depending on whether the device is phone or
   * tablet.
   * <p>
   * Additionally, if a LinearLayoutManager is used this value will be forwarded to {@link
   * LinearLayoutManager#setInitialPrefetchItemCount(int)} as a performance optimization.
   * <p>
   * If you want to change the prefetch count without changing the view size you can simply use
   * {@link #setInitialPrefetchItemCount(int)}
   */
  @ModelProp(group = "prefetch")
  public void setNumViewsToShowOnScreen(float viewCount) {
    // The model's default is 0, ignore it in that case
    // 0 doesn't make sense as a user defined value
    if (viewCount != 0) {
      if (viewCount < 0) {
        throw new IllegalStateException("View count must be greater than 0");
      }

      numViewsToShowOnScreen = viewCount;
      setInitialPrefetchItemCount((int) Math.ceil(viewCount));
    }
  }

  /**
   * If you are using a Linear or Grid layout manager you can use this to set the item prefetch
   * count. Only use this if you are not using {@link #setNumViewsToShowOnScreen(float)}
   *
   * @see #setNumViewsToShowOnScreen(float)
   * @see LinearLayoutManager#setInitialPrefetchItemCount(int)
   */
  @ModelProp(group = "prefetch")
  public void setInitialPrefetchItemCount(int numItemsToPrefetch) {
    if (numItemsToPrefetch != 0) { // Ignore the default prop value when the user did not set one
      LayoutManager layoutManager = getLayoutManager();
      if (layoutManager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) layoutManager).setInitialPrefetchItemCount(numItemsToPrefetch);
      }
    }
  }

  @Override
  public void onChildAttachedToWindow(View child) {
    if (numViewsToShowOnScreen > 0) {
      ViewGroup.LayoutParams childLayoutParams = child.getLayoutParams();
      child.setTag(R.id.epoxy_recycler_view_child_initial_size_id, childLayoutParams.width);

      int childMargin = 0;
      if (childLayoutParams instanceof MarginLayoutParams) {
        MarginLayoutParams marginLayoutParams = (MarginLayoutParams) childLayoutParams;
        childMargin = marginLayoutParams.leftMargin + marginLayoutParams.rightMargin;
      }

      boolean isScrollingHorizontally = getLayoutManager().canScrollHorizontally();
      int itemSizeInScrollingDirection =
          ((int) (getSpaceForChildren(isScrollingHorizontally) / numViewsToShowOnScreen)
              - childMargin);

      if (isScrollingHorizontally) {
        childLayoutParams.width = itemSizeInScrollingDirection;
      } else {
        childLayoutParams.height = itemSizeInScrollingDirection;
      }

      // We don't need to request layout because the layout manager will do that for us next
    }
  }

  private int getSpaceForChildren(boolean horizontal) {
    if (horizontal) {
      return getTotalWidthPx(this)
          - getPaddingLeft()
          - (getClipToPadding() ? getPaddingRight() : 0);
      // If child views will be showing through padding than we include just one side of padding
      // since when the list is at position 0 only the child towards the end of the list will show
      // through the padding.
    } else {
      return getTotalHeightPx(this)
          - getPaddingTop()
          - (getClipToPadding() ? getPaddingBottom() : 0);
    }
  }

  private static int getTotalWidthPx(View view) {
    if (view.getWidth() > 0) {
      // Can only get a width if we are laid out
      return view.getWidth();
    }

    // Fall back to measured height
    return view.getMeasuredWidth();
  }

  private static int getTotalHeightPx(View view) {
    if (view.getHeight() > 0) {
      return view.getHeight();
    }

    return view.getMeasuredHeight();
  }

  @Override
  public void onChildDetachedFromWindow(View child) {
    if (numViewsToShowOnScreen > 0) {
      Object initialWidth = child.getTag(R.id.epoxy_recycler_view_child_initial_size_id);
      if (initialWidth instanceof Integer) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.width = (int) initialWidth;
        // No need to request layout since the view is unbound and not attached to window
      }
    }
  }

  /**
   * Set a dimension resource to specify the padding value to use on each side of the carousel.
   * <p>
   * If a resource is not set the padding will default to
   */
  @ModelProp
  public void setCarouselPadding(@DimenRes int paddingRes) {
    if (paddingRes == 0) {
      int px = (int) TypedValue
          .applyDimension(TypedValue.COMPLEX_UNIT_DIP, defaultPaddingDp,
              getResources().getDisplayMetrics());
      setPaddingInScrollDirection(px);
    } else {
      setPaddingInScrollDirection(getResources().getDimensionPixelOffset(paddingRes));
    }
  }


  @ModelProp
  public void setModels(List<? extends EpoxyModel<?>> models) {
    super.setModels(models);
  }

  @OnViewRecycled
  public void clear() {
    super.clear();
  }
}
