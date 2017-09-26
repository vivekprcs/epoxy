package com.airbnb.epoxy;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.epoxy.ModelView.Size;
import com.airbnb.viewmodeladapter.R;

import java.util.List;

@ModelView(saveViewState = true, autoLayout = Size.MATCH_WIDTH_WRAP_HEIGHT)
public class Carousel extends EpoxyRecyclerView {

  private float numViewsToShowOnScreen;

  public Carousel(Context context) {
    super(context);
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
   * Set the number of views to show on screen in the RecyclerView at a time, partial numbers are
   * allowed.
   * <p>
   * This is useful for nested RecyclerViews, where you want to easily control for the number of
   * items on screen, regardless of screen size. For example, you could set this to 1.2f so that one
   * view is shown in full 20% of the next view "peeks" from the edge to indicate that there is more
   * content to scroll to.
   * <p>
   * <p>
   * If a LinearLayoutManager is used this value will be forwarded to {@link
   * LinearLayoutManager#setInitialPrefetchItemCount(int)}.
   */
  @ModelProp
  public void setNumViewsToShowOnScreen(float viewCount) {
    // The model's default is 0, ignore it in that case
    // 0 doesn't make sense as a user defined value
    if (viewCount != 0) {
      if (viewCount < 0) {
        throw new IllegalStateException("View count must be greater than 0");
      }

      numViewsToShowOnScreen = viewCount;
      LayoutManager layoutManager = getLayoutManager();
      if (layoutManager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) layoutManager).setInitialPrefetchItemCount(
            (int) Math.ceil(viewCount));
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
        childMargin = (marginLayoutParams.leftMargin + marginLayoutParams.rightMargin) / 2;
      }
      float totalChildMargin = 2 * numViewsToShowOnScreen * childMargin;

      boolean isScrollingHorizontally = getLayoutManager().canScrollHorizontally();

      float spaceForChildren = getViewSizeInDirection(isScrollingHorizontally) - totalChildMargin;

      int itemSizeInScrollingDirection = Math.round(spaceForChildren / numViewsToShowOnScreen);

      if (isScrollingHorizontally) {
        childLayoutParams.width = itemSizeInScrollingDirection;
      } else {
        childLayoutParams.height = itemSizeInScrollingDirection;
      }

      // We don't need to request layout because the layout manager will do that for us next
    }
  }

  private int getViewSizeInDirection(boolean horizontal) {
    if (horizontal) {
      return getWidth() - getPaddingLeft() - getPaddingRight();
    } else {
      return getHeight() - getPaddingTop() - getPaddingBottom();
    }
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

  @ModelProp
  public void setModels(List<? extends EpoxyModel<?>> models) {
    super.setModels(models);
  }

  @OnViewRecycled
  public void clear() {
    super.clear();
  }
}
