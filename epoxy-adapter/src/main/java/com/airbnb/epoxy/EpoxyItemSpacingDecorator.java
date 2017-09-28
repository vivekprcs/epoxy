package com.airbnb.epoxy;

import android.graphics.Rect;
import android.support.annotation.Px;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemDecoration;
import android.support.v7.widget.RecyclerView.LayoutManager;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;

/**
 * Modifies item spacing in a recycler view so that items are equally spaced no matter where they
 * are on the grid. Only designed to work with standard linear or grid layout managers.
 */
public class EpoxyItemSpacingDecorator extends ItemDecoration {
  private int innerPaddingPx;
  private boolean layoutReversed;
  private boolean verticallyScrolling;
  private boolean horizontallyScrolling;
  private int itemCount;
  boolean firstItem;
  boolean lastItem;
  private int position;
  private Rect outRect;
  private boolean grid;

  private int spanSize;
  private int spanCount;
  private int spanIndex;
  private int endSpanIndex;
  private boolean isFirstItemInRow;
  private boolean fillsLastSpan;
  private boolean isInFirstRow;
  private boolean isInLastRow;

  public EpoxyItemSpacingDecorator() {
    this(0);
  }

  public EpoxyItemSpacingDecorator(@Px int pxBetweenItems) {
    setPxBetweenItems(pxBetweenItems);
  }

  public boolean setPxBetweenItems(@Px int pxBetweenItems) {
    int newSpacing = pxBetweenItems / 2;
    boolean changed = newSpacing != innerPaddingPx;
    innerPaddingPx = newSpacing;
    return changed;
  }

  @Px
  public int getPxBetweenItems() {
    return innerPaddingPx * 2;
  }

  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
    // Zero everything out for the common case
    outRect.setEmpty();
    this.outRect = outRect;

    position = parent.getChildAdapterPosition(view);
    if (position == RecyclerView.NO_POSITION) {
      // View is not shown
      return;
    }

    itemCount = parent.getAdapter().getItemCount();
    firstItem = position == 0;
    lastItem = position == itemCount - 1;
    LayoutManager layout = parent.getLayoutManager();
    horizontallyScrolling = layout.canScrollHorizontally();
    verticallyScrolling = layout.canScrollVertically();
    grid = layout instanceof GridLayoutManager;

    if (grid) {
      GridLayoutManager grid = (GridLayoutManager) layout;
      final SpanSizeLookup spanSizeLookup = grid.getSpanSizeLookup();
      spanSize = spanSizeLookup.getSpanSize(position);
      spanCount = grid.getSpanCount();
      spanIndex = spanSizeLookup.getSpanIndex(position, spanCount);
      isFirstItemInRow = spanIndex == 0;
      fillsLastSpan = spanIndex + spanSize == spanCount;
      isInFirstRow = isInFirstRow(position, spanSizeLookup, spanCount);
      isInLastRow =
          !isInFirstRow && isInLastRow(position, itemCount, spanSizeLookup, spanCount);
    }

    layoutReversed = shouldReverseLayout(layout, horizontallyScrolling);

    boolean left = getLeftPadding();
    boolean right = getRightPadding();
    boolean top = getTopPadding();
    boolean bottom = getBottomPadding();

    if (layoutReversed) {
      if (horizontallyScrolling) {
        boolean temp = left;
        left = right;
        right = temp;
      } else {
        boolean temp = top;
        top = bottom;
        bottom = temp;
      }
    }

    outRect.right = right ? innerPaddingPx : 0;
    outRect.left = left ? innerPaddingPx : 0;
    outRect.top = top ? innerPaddingPx : 0;
    outRect.bottom = bottom ? innerPaddingPx : 0;
  }

  private static boolean shouldReverseLayout(LayoutManager layout, boolean horizontallyScrolling) {
    boolean reverseLayout =
        layout instanceof LinearLayoutManager && ((LinearLayoutManager) layout).getReverseLayout();
    boolean rtl = layout.getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
    if (horizontallyScrolling && rtl) {
      // This is how linearlayout checks if it should reverse layout in #resolveShouldLayoutReverse
      reverseLayout = !reverseLayout;
    }

    return reverseLayout;
  }

  private boolean getBottomPadding() {
    if (grid) {
      return (horizontallyScrolling && !fillsLastSpan)
          || (verticallyScrolling && !isInLastRow);
    }

    return verticallyScrolling && !lastItem;
  }

  private boolean getTopPadding() {
    if (grid) {
      return (horizontallyScrolling && !isFirstItemInRow)
          || (verticallyScrolling && !isInFirstRow);
    }

    return verticallyScrolling && !firstItem;
  }

  private boolean getRightPadding() {
    if (grid) {
      return (horizontallyScrolling && !isInLastRow)
          || (verticallyScrolling && !fillsLastSpan);
    }

    return horizontallyScrolling && !lastItem;
  }

  private boolean getLeftPadding() {
    if (grid) {
      return (horizontallyScrolling && !isInFirstRow)
          || (verticallyScrolling && !isFirstItemInRow);
    }

    return horizontallyScrolling && !firstItem;
  }

  private static boolean isInFirstRow(int position, SpanSizeLookup spanSizeLookup, int spanCount) {
    int totalSpan = 0;
    for (int i = 0; i <= position; i++) {
      totalSpan += spanSizeLookup.getSpanSize(i);
      if (totalSpan > spanCount) {
        return false;
      }
    }

    return true;
  }

  private static boolean isInLastRow(int position, int itemCount, SpanSizeLookup spanSizeLookup,
      int spanCount) {
    int totalSpan = 0;
    for (int i = itemCount - 1; i >= position; i--) {
      totalSpan += spanSizeLookup.getSpanSize(i);
      if (totalSpan > spanCount) {
        return false;
      }
    }

    return true;
  }
}