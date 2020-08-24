/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zeoflow.material.elements.tooltip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.graphics.ColorUtils;

import com.zeoflow.R;
import com.zeoflow.material.elements.color.MaterialColors;
import com.zeoflow.material.elements.internal.TextDrawableHelper;
import com.zeoflow.material.elements.internal.ThemeEnforcement;
import com.zeoflow.material.elements.resources.MaterialResources;
import com.zeoflow.material.elements.resources.TextAppearance;
import com.zeoflow.material.elements.shape.EdgeTreatment;
import com.zeoflow.material.elements.shape.MarkerEdgeTreatment;
import com.zeoflow.material.elements.shape.MaterialShapeDrawable;
import com.zeoflow.material.elements.shape.OffsetEdgeTreatment;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * A Tooltip that supports shape theming and draws a pointer on the bottom in the center of the
 * supplied bounds. Additional margin can be applied which will prevent the main bubble of the
 * Tooltip from being drawn too close to the edge of the window.
 *
 * <p>Note: {@link #setRelativeToView(View)} should be called so {@code TooltipDrawable} can
 * calculate where it is being drawn within the visible display.
 */
@RestrictTo(LIBRARY_GROUP)
public class TooltipDrawable extends MaterialShapeDrawable implements TextDrawableHelper.TextDrawableDelegate
{

  @StyleRes
  private static final int DEFAULT_STYLE = R.style.Widget_MaterialElements_Tooltip;
  @AttrRes
  private static final int DEFAULT_THEME_ATTR = R.attr.tooltipStyle;
  @NonNull
  private final Context context;
  @Nullable
  private final FontMetrics fontMetrics = new FontMetrics();
  @NonNull
  private final TextDrawableHelper textDrawableHelper =
      new TextDrawableHelper(/* delegate= */ this);
  @NonNull
  private final Rect displayFrame = new Rect();
  @Nullable
  private CharSequence text;
  private int padding;
  private int minWidth;
  private int minHeight;
  private int layoutMargin;
  private int arrowSize;
  private int locationOnScreenX;
  @NonNull
  private final OnLayoutChangeListener attachedViewLayoutChangeListener =
      new OnLayoutChangeListener()
      {
        @Override
        public void onLayoutChange(
            View v,
            int left,
            int top,
            int right,
            int bottom,
            int oldLeft,
            int oldTop,
            int oldRight,
            int oldBottom)
        {
          updateLocationOnScreen(v);
        }
      };

  private TooltipDrawable(
      @NonNull Context context,
      AttributeSet attrs,
      @AttrRes int defStyleAttr,
      @StyleRes int defStyleRes)
  {
    super(context, attrs, defStyleAttr, defStyleRes);
    this.context = context;
    textDrawableHelper.getTextPaint().density = context.getResources().getDisplayMetrics().density;
    textDrawableHelper.getTextPaint().setTextAlign(Align.CENTER);
  }

  /**
   * Returns a TooltipDrawable from the given attributes.
   */
  @NonNull
  public static TooltipDrawable createFromAttributes(
      @NonNull Context context,
      @Nullable AttributeSet attrs,
      @AttrRes int defStyleAttr,
      @StyleRes int defStyleRes)
  {
    TooltipDrawable tooltip = new TooltipDrawable(context, attrs, defStyleAttr, defStyleRes);
    tooltip.loadFromAttributes(attrs, defStyleAttr, defStyleRes);

    return tooltip;
  }

  /**
   * Returns a TooltipDrawable from the given attributes.
   */
  @NonNull
  public static TooltipDrawable createFromAttributes(
      @NonNull Context context, @Nullable AttributeSet attrs)
  {
    return createFromAttributes(context, attrs, DEFAULT_THEME_ATTR, DEFAULT_STYLE);
  }

  @NonNull
  public static TooltipDrawable create(@NonNull Context context)
  {
    return createFromAttributes(context, null, DEFAULT_THEME_ATTR, DEFAULT_STYLE);
  }

  private void loadFromAttributes(
      @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes)
  {
    TypedArray a =
        ThemeEnforcement.obtainStyledAttributes(
            context, attrs, R.styleable.Tooltip, defStyleAttr, defStyleRes);

    arrowSize = context.getResources().getDimensionPixelSize(R.dimen.mtrl_tooltip_arrowSize);
    setShapeAppearanceModel(
        getShapeAppearanceModel().toBuilder().setBottomEdge(createMarkerEdge()).build());

    setText(a.getText(R.styleable.Tooltip_android_text));
    setTextAppearance(
        MaterialResources.getTextAppearance(
            context, a, R.styleable.Tooltip_android_textAppearance));

    int onBackground =
        MaterialColors.getColor(
            context, R.attr.colorOnBackground, TooltipDrawable.class.getCanonicalName());
    int background =
        MaterialColors.getColor(
            context, android.R.attr.colorBackground, TooltipDrawable.class.getCanonicalName());

    int backgroundTintDefault =
        MaterialColors.layer(
            ColorUtils.setAlphaComponent(background, (int) (0.9f * 255)),
            ColorUtils.setAlphaComponent(onBackground, (int) (0.6f * 255)));
    setFillColor(
        ColorStateList.valueOf(
            a.getColor(R.styleable.Tooltip_backgroundTint, backgroundTintDefault)));

    setStrokeColor(
        ColorStateList.valueOf(
            MaterialColors.getColor(
                context, R.attr.colorSurface, TooltipDrawable.class.getCanonicalName())));

    padding = a.getDimensionPixelSize(R.styleable.Tooltip_android_padding, 0);
    minWidth = a.getDimensionPixelSize(R.styleable.Tooltip_android_minWidth, 0);
    minHeight = a.getDimensionPixelSize(R.styleable.Tooltip_android_minHeight, 0);
    layoutMargin = a.getDimensionPixelSize(R.styleable.Tooltip_android_layout_margin, 0);

    a.recycle();
  }

  /**
   * Return the text that TooltipDrawable is displaying.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_text
   */
  @Nullable
  public CharSequence getText()
  {
    return text;
  }

  /**
   * Sets the text to be displayed.
   *
   * @param text text to be displayed
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_text
   * @see #setTextResource(int)
   */
  public void setText(@Nullable CharSequence text)
  {
    if (!TextUtils.equals(this.text, text))
    {
      this.text = text;
      textDrawableHelper.setTextWidthDirty(true);
      invalidateSelf();
    }
  }

  /**
   * Sets the text to be displayed using a string resource identifier.
   *
   * @param id the resource identifier of the string resource to be displayed
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_text
   * @see #setText(CharSequence)
   */
  public void setTextResource(@StringRes int id)
  {
    setText(context.getResources().getString(id));
  }

  /**
   * Returns the TextAppearance used by this tooltip.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_textAppearance
   */
  @Nullable
  public TextAppearance getTextAppearance()
  {
    return textDrawableHelper.getTextAppearance();
  }

  /**
   * Sets this tooltip's text appearance.
   *
   * @param textAppearance This tooltip's text appearance.
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_textAppearance
   */
  public void setTextAppearance(@Nullable TextAppearance textAppearance)
  {
    textDrawableHelper.setTextAppearance(textAppearance, context);
  }

  /**
   * Sets this tooltip's text appearance using a resource id.
   *
   * @param id The resource id of this tooltip's text appearance.
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_textAppearance
   */
  public void setTextAppearanceResource(@StyleRes int id)
  {
    setTextAppearance(new TextAppearance(context, id));
  }

  /**
   * Returns the minimum width of TooltipDrawable in terms of pixels.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_minWidth
   * @see #setMinWidth(int)
   */
  public int getMinWidth()
  {
    return minWidth;
  }

  /**
   * Sets the width of the TooltipDrawable to be at least {@code minWidth} wide.
   *
   * @param minWidth the minimum width of TooltipDrawable in terms of pixels
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_minWidth
   * @see #getMinWidth()
   */
  public void setMinWidth(@Px int minWidth)
  {
    this.minWidth = minWidth;
    invalidateSelf();
  }

  /**
   * Returns the minimum height of TooltipDrawable in terms of pixels.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_minHeight
   * @see #setMinHeight(int)
   */
  public int getMinHeight()
  {
    return minHeight;
  }

  /**
   * Sets the height of the TooltipDrawable to be at least {@code minHeight} wide.
   *
   * @param minHeight the minimum height of TooltipDrawable in terms of pixels
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_minHeight
   * @see #getMinHeight()
   */
  public void setMinHeight(@Px int minHeight)
  {
    this.minHeight = minHeight;
    invalidateSelf();
  }

  /**
   * Returns the padding between the text of TooltipDrawable and the sides in terms of pixels.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_padding
   * @see #setTextPadding(int)
   */
  public int getTextPadding()
  {
    return padding;
  }

  /**
   * Sets the padding between the text of the TooltipDrawable and the sides to be {@code padding}.
   *
   * @param padding the padding to use around the text
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_padding
   * @see #getTextPadding()
   */
  public void setTextPadding(@Px int padding)
  {
    this.padding = padding;
    invalidateSelf();
  }

  /**
   * Returns the margin around the TooltipDrawable.
   *
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_layout_margin
   * @see #setLayoutMargin(int)
   */
  public int getLayoutMargin()
  {
    return layoutMargin;
  }

  /**
   * Sets the margin around the TooltipDrawable to be {@code margin}.
   *
   * @param layoutMargin the margin to use around the TooltipDrawable
   * @attr ref com.google.android.material.R.styleable#Tooltip_android_layout_margin
   * @see #getLayoutMargin()
   */
  public void setLayoutMargin(@Px int layoutMargin)
  {
    this.layoutMargin = layoutMargin;
    invalidateSelf();
  }

  /**
   * Should be called to allow this drawable to calculate its position within the current display
   * frame. This allows it to apply to specified window padding.
   *
   * @see #detachView(View)
   */
  public void setRelativeToView(@Nullable View view)
  {
    if (view == null)
    {
      return;
    }
    updateLocationOnScreen(view);
    // Listen for changes that indicate the view has moved so the location can be updated
    view.addOnLayoutChangeListener(attachedViewLayoutChangeListener);
  }

  /**
   * Should be called when the view is detached from the screen.
   *
   * @see #setRelativeToView(View)
   */
  public void detachView(@Nullable View view)
  {
    if (view == null)
    {
      return;
    }
    view.removeOnLayoutChangeListener(attachedViewLayoutChangeListener);
  }

  @Override
  public int getIntrinsicWidth()
  {
    return (int) Math.max(2 * padding + getTextWidth(), minWidth);
  }

  @Override
  public int getIntrinsicHeight()
  {
    return (int) Math.max(textDrawableHelper.getTextPaint().getTextSize(), minHeight);
  }

  @Override
  public void draw(@NonNull Canvas canvas)
  {
    canvas.save();

    // Translate the canvas by the same about that the pointer is offset to keep it pointing at the
    // same place relative to the bounds.
    float translateX = calculatePointerOffset();

    // Handle the extra space created by the arrow notch at the bottom of the tooltip by moving the
    // canvas. This allows the pointing part of the tooltip to align with the bottom of the bounds.
    float translateY = (float) -(arrowSize * Math.sqrt(2) - arrowSize);

    canvas.translate(translateX, translateY);

    // Draw the background.
    super.draw(canvas);

    // Draw the text.
    drawText(canvas);

    canvas.restore();
  }

  @Override
  protected void onBoundsChange(Rect bounds)
  {
    super.onBoundsChange(bounds);

    // Update the marker edge since the location of the marker arrow can move depending on the the
    // bounds.
    setShapeAppearanceModel(
        getShapeAppearanceModel().toBuilder().setBottomEdge(createMarkerEdge()).build());
  }

  @Override
  public boolean onStateChange(int[] state)
  {
    // Exposed for TextDrawableDelegate.
    return super.onStateChange(state);
  }

  @Override
  public void onTextSizeChange()
  {
    invalidateSelf();
  }

  private void updateLocationOnScreen(@NonNull View v)
  {
    int[] locationOnScreen = new int[2];
    v.getLocationOnScreen(locationOnScreen);
    locationOnScreenX = locationOnScreen[0];
    v.getWindowVisibleDisplayFrame(displayFrame);
  }

  private float calculatePointerOffset()
  {
    float pointerOffset = 0;
    if (displayFrame.right - getBounds().right - locationOnScreenX - layoutMargin < 0)
    {
      pointerOffset = displayFrame.right - getBounds().right - locationOnScreenX - layoutMargin;
    } else if (displayFrame.left - getBounds().left - locationOnScreenX + layoutMargin > 0)
    {
      pointerOffset = displayFrame.left - getBounds().left - locationOnScreenX + layoutMargin;
    }
    return pointerOffset;
  }

  private EdgeTreatment createMarkerEdge()
  {
    float offset = -calculatePointerOffset();
    // The maximum distance the arrow can be offset before extends outside the bounds.
    float maxArrowOffset = (float) (getBounds().width() - arrowSize * Math.sqrt(2)) / 2.0f;
    offset = Math.max(offset, -maxArrowOffset);
    offset = Math.min(offset, maxArrowOffset);
    return new OffsetEdgeTreatment(new MarkerEdgeTreatment(arrowSize), offset);
  }

  private void drawText(@NonNull Canvas canvas)
  {
    if (text == null)
    {
      // If text is null there's nothing to draw.
      return;
    }

    Rect bounds = getBounds();
    int y = (int) calculateTextOriginAndAlignment(bounds);

    if (textDrawableHelper.getTextAppearance() != null)
    {
      textDrawableHelper.getTextPaint().drawableState = getState();
      textDrawableHelper.updateTextPaintDrawState(context);
    }

    canvas.drawText(text, 0, text.length(), bounds.centerX(), y, textDrawableHelper.getTextPaint());
  }

  private float getTextWidth()
  {
    if (text == null)
    {
      return 0;
    }
    return textDrawableHelper.getTextWidth(text.toString());
  }

  /**
   * Calculates the text origin and alignment based on the bounds.
   */
  private float calculateTextOriginAndAlignment(@NonNull Rect bounds)
  {
    return bounds.centerY() - calculateTextCenterFromBaseline();
  }

  /**
   * Calculates the offset from the visual center of the text to its baseline.
   *
   * <p>To draw the text, we provide the origin to {@link Canvas#drawText(CharSequence, int, int,
   * float, float, Paint)}. This origin always corresponds vertically to the text's baseline.
   * Because we need to vertically center the text, we need to calculate this offset.
   *
   * <p>Note that tooltips that share the same font must have consistent text baselines despite
   * having different text strings. This is why we calculate the vertical center using {@link
   * Paint#getFontMetrics(FontMetrics)} rather than {@link Paint#getTextBounds(String, int, int,
   * Rect)}.
   */
  private float calculateTextCenterFromBaseline()
  {
    textDrawableHelper.getTextPaint().getFontMetrics(fontMetrics);
    return (fontMetrics.descent + fontMetrics.ascent) / 2f;
  }
}
