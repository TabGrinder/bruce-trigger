package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Bounded draw-call renderer. Fonts and animation timing are not exact in stock protocol.
 */
final class MirrorView extends View {
  private final NativePowerDialog powerDialog=new NativePowerDialog();
  private Bitmap bitmap;
  private Canvas target;
  private Bitmap gameBack;
  private Canvas gameBackCanvas;
  private final GameSprites gameSprites = new GameSprites();
  private final WindowBudget gamePixels = new WindowBudget(8192,4194304,1000);
  private final Paint p = new Paint();
  private final Paint viewPaint = new Paint();
  private final Paint gradientPaint=new Paint();
  private final Canvas spriteCanvas=new Canvas();
  private Bitmap spriteCanvasBitmap;
  private Bitmap glyphAtlas;
  private PorterDuffColorFilter glyphColorFilter;
  private int glyphFilterColor;
  private final Rect glyphSource = new Rect();
  private final RectF glyphTarget = new RectF();
  private final RectF displayBounds = new RectF();
  private final Rect windowFrame = new Rect();
  private final Path gameMonoPath = new Path();
  private final Paint gameMonoPaint = new Paint();
  private final int[] gamePixelRow = new int[40];
  private long lastFrame;
  private boolean partial, gameBatch, gameContent;
  private long contentVersion, presentedVersion, presentedUpdates, lastPresentedAt;
  private long gameFramesComposed, presentedGameFrame;

  long presentedUpdates() { return presentedUpdates; }
  long gameFramesComposed() { return gameFramesComposed; }
  long presentedGameFrame() { return presentedGameFrame; }
  long presentationAge(long now) { return lastPresentedAt == 0 ? -1 : Math.max(0, now-lastPresentedAt); }

  boolean applyGameFrame(java.util.List<byte[]> frame) {
    if (frame == null || frame.isEmpty() || frame.size() > 256) return false;
    Bitmap front = bitmap;
    Canvas frontCanvas = target;
    long previousVersion = contentVersion, previousTime = lastFrame;
    boolean complete = false;
    gameBatch = true;
    gameSprites.begin();
    try {
      if (front != null) {
        if (gameBack == null || gameBack.getWidth() != front.getWidth()
            || gameBack.getHeight() != front.getHeight()) {
          if (gameBack != null) gameBack.recycle();
          gameBack = Bitmap.createBitmap(front.getWidth(), front.getHeight(), Bitmap.Config.RGB_565);
          gameBackCanvas = new Canvas(gameBack);
        }
        byte[] first=frame.get(0);
        boolean replacesScreen=gameSprites.selected()==0 && first!=null
            && FrameValidator.valid(first) && (first[2]&255)==0;
        // A validated screen fill replaces every pixel. Partial draws still
        // copy the prior complete frame and retain transactional rollback.
        if(!replacesScreen)gameBackCanvas.drawBitmap(front, 0, 0, null);
        bitmap = gameBack;
        target = gameBackCanvas;
      }
      for (byte[] packet : frame) {
        // Approved frames establish geometry once; deltas cannot resize the front buffer.
        if (front != null && packet != null && packet.length > 2 && (packet[2] & 255) == 99) {
          reject("Game geometry changed after initialization"); return false;
        }
        if (!applyGamePacket(packet)) return false;
      }
      gameBack = front;
      gameBackCanvas = frontCanvas;
      gameFramesComposed++;
      gameContent = true;
      complete = true;
      postInvalidateOnAnimation();
      return true;
    } catch (OutOfMemoryError | RuntimeException e) {
      reject("Game frame composition failed");
      return false;
    } finally {
      if (!complete) {
        if (bitmap != front) { gameBack = bitmap; gameBackCanvas = target; }
        bitmap = front; target = frontCanvas;
        contentVersion = previousVersion; lastFrame = previousTime;
      }
      gameSprites.finish(complete);
      gameBatch = false;
    }
  }
  private boolean applyGamePacket(byte[] b) {
    if (b==null || b.length<3) return false;
    int op=b[2]&255;
    if(op>=90 && op<=96) {
      if(!GameDrawValidator.valid(b))return false;
      long now=android.os.SystemClock.elapsedRealtime();
      int cost=op==90?u(b,5)*u(b,7):op==92?gameSprites.area(u(b,3)):op==94?u(b,7):(op==95||op==96)?u(b,7)*u(b,9):1;
      if(!gamePixels.allow(cost,now) || !draws.allow(op==95?Math.max(1,u(b,15)==0?u(b,7):u(b,9)):1,now)) {
        reject("Game graphics exceeded the processing budget");return false;
      }
      if(op==90)gameSprites.create(u(b,3),u(b,5),u(b,7));
      else if(op==91)gameSprites.select(u(b,3));
      else if(op==92){gameSprites.push(target,u(b,3),s(b,5),s(b,7),u(b,9));contentVersion++;lastFrame=android.os.SystemClock.elapsedRealtime();}
      else if(op==93)gameSprites.delete(u(b,3));
      if(op!=95 && op!=94 && op!=96)return true;
    }
    Bitmap screen=bitmap;Canvas screenCanvas=target;
    Bitmap sprite=gameSprites.writable();
    if(sprite!=null){bitmap=sprite;if(spriteCanvasBitmap!=sprite){spriteCanvas.setBitmap(sprite);spriteCanvasBitmap=sprite;}target=spriteCanvas;}
    try {
      if(op==96) {
        int x=s(b,3),y=s(b,5),w=u(b,7),h=u(b,9),stride=(w+7)/8;
        int fg=color(u(b,11)),bg=color(u(b,13));boolean opaque=u(b,15)==1;
        int left=Math.max(0,-x),right=Math.min(w,bitmap.getWidth()-x);
        int top=Math.max(0,-y),bottom=Math.min(h,bitmap.getHeight()-y);
        // Draw only covered bits. Reading/re-writing an entire RGB565 row for
        // transparent masks can quantize untouched colors repeatedly on Android.
        // One bounded reusable path avoids per-pixel calls and preserves the
        // existing background exactly, including overlapping colored masks.
        gameMonoPaint.reset();gameMonoPaint.setAntiAlias(false);
        gameMonoPaint.setDither(false);gameMonoPaint.setStyle(Paint.Style.FILL);
        if(right>left && bottom>top) {
          if(opaque){gameMonoPaint.setColor(bg);target.drawRect(x+left,y+top,x+right,y+bottom,gameMonoPaint);}
          gameMonoPath.rewind();
          for(int row=top;row<bottom;row++) {
            int col=left;
            while(col<right) {
              while(col<right&&(b[17+row*stride+col/8]&(1<<(col%8)))==0)col++;
              int begin=col;
              while(col<right&&(b[17+row*stride+col/8]&(1<<(col%8)))!=0)col++;
              if(col>begin)gameMonoPath.addRect(x+begin,y+row,x+col,y+row+1,Path.Direction.CW);
            }
          }
          gameMonoPaint.setColor(fg);target.drawPath(gameMonoPath,gameMonoPaint);
        }
        contentVersion++;lastFrame=android.os.SystemClock.elapsedRealtime();return true;
      }
      if(op==94) {
        int x=s(b,3),y=s(b,5),n=u(b,7),start=Math.max(0,-x),end=Math.min(n,bitmap.getWidth()-x);
        if(y>=0 && y<bitmap.getHeight() && end>start) {
          for(int i=start;i<end;i++)gamePixelRow[i-start]=color(u(b,9+2*i));
          bitmap.setPixels(gamePixelRow,0,end-start,x+start,y,end-start,1);
        }
        contentVersion++;lastFrame=android.os.SystemClock.elapsedRealtime();return true;
      }
      if(op==95) {
        int x=s(b,3),y=s(b,5),w=u(b,7),h=u(b,9),steps=u(b,15)==0?w:h;
        Paint gradient=gradientPaint;gradient.setStyle(Paint.Style.FILL);
        int c1=u(b,11),c2=u(b,13);
        for(int i=0;i<steps;i++) {
          float f=steps<2?0:i/(float)(steps-1);
          int rr=Math.round((c1>>11&31)*(1-f)+(c2>>11&31)*f),gg=Math.round((c1>>5&63)*(1-f)+(c2>>5&63)*f),bb=Math.round((c1&31)*(1-f)+(c2&31)*f);
          gradient.setColor(color((rr<<11)|(gg<<5)|bb));
          if(u(b,15)==0)target.drawRect(x+i,y,x+i+1,y+h,gradient);else target.drawRect(x,y+i,x+w,y+i+1,gradient);
        }
        contentVersion++;lastFrame=android.os.SystemClock.elapsedRealtime();return true;
      }
      return apply(b);
    } finally {if(sprite!=null){bitmap=screen;target=screenCanvas;}}
  }
  private int rejected;
  private long totalRejected, imagePackets;
  private String lastRejection = "None";
  private final WindowBudget draws = new WindowBudget(8192, 8192, 1000);
  private final ThemeImages images = new ThemeImages();
  private long renderedImages, gifPreviews;
  private String menuRenderKey;
  private boolean menuRenderAsset;
  void loadImages(java.util.Map<String,byte[]> source) { menuRenderKey=null;images.load(source); }
  void mergeImages(java.util.Map<String,byte[]> source) { menuRenderKey=null;images.merge(source); }
  String missingImageKey(byte[] record) { return images.missingKey(record); }
  boolean showMenu(RemoteMenu menu,java.util.Map<String,String> mapping,int foreground,int background) {
    String signature=menu.identity()+"\n"+menu.active+":"+menu.width+":"+menu.height+":"+foreground+":"+background+":"+menu.imageKey(mapping);
    if(bitmap!=null && signature.equals(menuRenderKey)) {
      lastFrame=android.os.SystemClock.elapsedRealtime();return menuRenderAsset;
    }
    boolean reuse=menuRenderKey!=null && bitmap!=null && !gameContent
        && bitmap.getWidth()==menu.width && bitmap.getHeight()==menu.height;
    if(!reuse)clearScreen();
    try {
      if(!reuse){bitmap=Bitmap.createBitmap(menu.width,menu.height,Bitmap.Config.RGB_565);target=new Canvas(bitmap);}
      target.drawColor(color(background));p.reset();p.setAntiAlias(false);p.setFilterBitmap(false);
      String key=menu.imageKey(mapping);Bitmap asset=key==null?null:images.getKey(key);
      if(asset!=null) {
        target.drawBitmap(asset,(menu.width-asset.getWidth())/2f,(menu.height-asset.getHeight())/2f,p);
        renderedImages++;
      } else {
        if(!menu.kind.equals("main_menu")) {clearScreen();return false;}
        if(foreground==background)foreground=background==0xffff?0:0xffff;
        p.setColor(color(foreground));p.setTypeface(Typeface.MONOSPACE);
        int scale=Math.max(1,menu.height/170);p.setTextSize(16*scale);
        target.drawText(menu.title.length()==0?"Menu":menu.title,8,22*scale,p);
        int row=24*scale,visible=Math.max(1,(menu.height-32*scale)/row),start=Math.max(0,Math.min(menu.active-visible/2,menu.labels.size()-visible));
        for(int i=start;i<menu.labels.size()&&i<start+visible;i++) {
          int y=48*scale+(i-start)*row;
          if(i==menu.active){p.setColor(color(foreground));target.drawRect(4,y-18*scale,menu.width-4,y+4*scale,p);p.setColor(color(background));}
          else p.setColor(color(foreground));
          target.drawText(menu.labels.get(i),10,y,p);
        }
      }
      contentVersion++;lastFrame=android.os.SystemClock.elapsedRealtime();setContentDescription("Bruce menu snapshot: "+menu.selected());
      menuRenderKey=signature;menuRenderAsset=asset!=null;if(!reuse)requestLayout();postInvalidateOnAnimation();return menuRenderAsset;
    }catch(RuntimeException|OutOfMemoryError e){clearScreen();return false;}
  }
  void clearImages() { menuRenderKey=null;images.clear(); }
  long renderedImages() { return renderedImages; }
  long gifPreviews() { return gifPreviews; }
  private final WindowBudget geometry = new WindowBudget(4, 4, 10000);

  MirrorView(Context c) {
    super(c);
    setContentDescription("No received screen. Use CONNECT.");
  }

  void reset() {
    clearScreen();
    contentVersion = presentedVersion = presentedUpdates = lastPresentedAt = gameFramesComposed = presentedGameFrame = 0;
    totalRejected = imagePackets = renderedImages = gifPreviews = 0; lastRejection = "None";
  }

  /** Erase stale pixels while retaining the last capture's diagnostic evidence. */
  void clearScreen() {
    spriteCanvas.setBitmap(null);spriteCanvasBitmap=null;gameSprites.clear();gamePixels.reset();powerDialog.reset();
    menuRenderKey=null;
    if (gameBack != null && gameBack != bitmap) gameBack.recycle();
    gameBack = null; gameBackCanvas = null;
    if (bitmap != null) bitmap.recycle();
    bitmap = null;
    target = null;
    lastFrame = 0;
    gameContent = false;
    partial = false;
    rejected = 0;
    geometry.reset();
    draws.reset();
    setContentDescription("No received screen. Use CONNECT.");
    invalidate();
  }

  long lastFrame() {
    return lastFrame;
  }

  boolean partial() {
    return partial;
  }

  int rejected() {
    return rejected;
  }
  long totalRejected() { return totalRejected; }
  long imagePackets() { return imagePackets; }
  String lastRejection() { return lastRejection; }

  private void ensureGlyphAtlas() {
    if (glyphAtlas != null) return;
    int[] pixels = new int[96 * 128];
    for (int g=0;g<256;g++) for (int x=0;x<5;x++) {
      int bits=StockGlyphs.column(g,x);
      for (int y=0;y<8;y++) if ((bits&(1<<y))!=0)
        pixels[((g/16)*8+y)*96+(g%16)*6+x]=Color.WHITE;
    }
    glyphAtlas=Bitmap.createBitmap(pixels,96,128,Bitmap.Config.ARGB_8888);
  }

  private void drawGlyph(int glyph,int x,int y,int scale,int fg,int bg,boolean opaque) {
    if (x>=bitmap.getWidth() || y>=bitmap.getHeight() || x+6*scale<=0 || y+8*scale<=0) return;
    p.setColorFilter(null);
    p.setStyle(Paint.Style.FILL);
    if(opaque){p.setColor(color(bg));target.drawRect(x,y,x+6*scale,y+8*scale,p);}
    glyphSource.set((glyph%16)*6,(glyph/16)*8,(glyph%16)*6+6,(glyph/16)*8+8);
    glyphTarget.set(x,y,x+6*scale,y+8*scale);
    if(glyphColorFilter==null || glyphFilterColor!=fg){glyphFilterColor=fg;glyphColorFilter=new PorterDuffColorFilter(color(fg),PorterDuff.Mode.SRC_IN);}
    p.setColorFilter(glyphColorFilter);
    p.setFilterBitmap(false);
    target.drawBitmap(glyphAtlas,glyphSource,glyphTarget,p);
    p.setColorFilter(null);
  }

  private static int u(byte[] b, int i) {
    return FrameDecoder.u16(b, i);
  }

  private static int s(byte[] b, int i) {
    return (short) u(b, i);
  }

  private static int color(int rgb) {
    return Color.rgb(
        ((rgb >> 11) & 31) * 255 / 31, ((rgb >> 5) & 63) * 255 / 63, (rgb & 31) * 255 / 31);
  }

  boolean apply(byte[] b) {
    menuRenderKey=null;
    if (!FrameValidator.valid(b)) {
      reject();
      return false;
    }
    if(!gameBatch)b=powerDialog.text(b,bitmap==null?0:bitmap.getWidth(),bitmap==null?0:bitmap.getHeight());
    int op = b[2] & 255;
    int[] length = {
      5, 13, 13, 15, 15, 11, 11, 17, 17, 13, 13, 13, 19, 17, 13, 13, 13, 13, 12, 0, 11, 11
    };
    if (op == 99) {
      if (b.length != 8) {
        reject();
        return false;
      }
      int w = u(b, 3), h = u(b, 5);
      if (w < 1 || h < 1 || w > 1024 || h > 1024 || (long) w * h > 1048576) {
        reject();
        return false;
      }
      if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
        if (!geometry.allow(1, android.os.SystemClock.elapsedRealtime())) {
          reject();
          return false;
        }
        Bitmap candidate = null;
        try {
          candidate = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
          Canvas next = new Canvas(candidate);
          if (bitmap != null) bitmap.recycle();
          bitmap = candidate;
          target = next;
          bitmap.eraseColor(Color.BLACK);
          lastFrame = 0;
        } catch (OutOfMemoryError e) {
          if (candidate != null) candidate.recycle();
          reject();
          return false;
        }
      }
      requestLayout();
      invalidate();
      return true;
    }
    if (op >= length.length
        || op == 19
        || b.length < length[op]
        || ((op < 14 || op > 18) && b.length != length[op])
        || target == null) {
      reject();
      return false;
    }
    if (!draws.allow(1, android.os.SystemClock.elapsedRealtime())) {
      reject();
      return false;
    }
    p.reset();
    p.setAntiAlias(false);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1);
    float x = op == 0 ? 0 : s(b, 3), y = op == 0 ? 0 : s(b, 5);
    switch (op) {
      case 0:
        target.drawColor(color(u(b, 3)));
        rejected = 0; partial = false;
        break;
      case 1:
      case 2:
      case 3:
      case 4:
        {
          float w = u(b, 7), h = u(b, 9);
          p.setColor(color(u(b, op < 3 ? 11 : 13)));
          p.setStyle((op == 2 || op == 4) ? Paint.Style.FILL : Paint.Style.STROKE);
          RectF r = new RectF(x, y, x + w, y + h);
          if (op < 3) target.drawRect(r, p);
          else target.drawRoundRect(r, u(b, 11), u(b, 11), p);
          break;
        }
      case 5:
      case 6:
        p.setColor(color(u(b, 9)));
        p.setStyle(op == 6 ? Paint.Style.FILL : Paint.Style.STROKE);
        target.drawCircle(x, y, u(b, 7), p);
        break;
      case 7:
      case 8:
        {
          Path path = new Path();
          path.moveTo(x, y);
          path.lineTo(s(b, 7), s(b, 9));
          path.lineTo(s(b, 11), s(b, 13));
          path.close();
          p.setColor(color(u(b, 15)));
          p.setStyle(op == 8 ? Paint.Style.FILL : Paint.Style.STROKE);
          target.drawPath(path, p);
          break;
        }
      case 9:
      case 10:
        p.setColor(color(u(b, 11)));
        p.setStyle(op == 10 ? Paint.Style.FILL : Paint.Style.STROKE);
        target.drawOval(x - u(b, 7), y - u(b, 9), x + u(b, 7), y + u(b, 9), p);
        break;
      case 11:
        p.setColor(color(u(b, 11)));
        target.drawLine(x, y, s(b, 7), s(b, 9), p);
        break;
      case 12:
        {
          float outer = u(b, 7), inner = u(b, 9);
          if (inner > outer) { reject(); return false; }
          float radius = (outer + inner) / 2;
          p.setStrokeWidth(Math.max(1, outer - inner));
          p.setColor(color(u(b, 15)));
          int start = u(b, 11) % 360, end = u(b, 13);
          int sweep = end - start;
          if (sweep < 0) sweep = ((sweep % 360) + 360) % 360;
          target.drawArc(new RectF(x-radius,y-radius,x+radius,y+radius), start+90,
              Math.min(sweep,360), false,p);
          partial = true;
          break;
        }
      case 13:
        p.setColor(color(u(b, 13)));
        p.setStrokeWidth(Math.min(u(b, 11), 1024));
        target.drawLine(x, y, s(b, 7), s(b, 9), p);
        partial = true;
        break;
      case 14:
      case 15:
      case 16:
      case 17:
        {
          try { ensureGlyphAtlas(); }
          catch (OutOfMemoryError e) { reject("Font atlas allocation failed"); return false; }
          if(!NativeText.render(b,bitmap.getWidth(),this::drawGlyph)) {
            reject("Unsupported text glyph or scale");return false;
          }
          partial = true; // Font/datum changes are not signalled by the stock protocol.
          break;
        }
      case 18:
        imagePackets++;
        Bitmap image = images.get(b);
        if (image == null) {
          reject(images.failure());
          return false;
        }
        if (u(b,7)>1) { reject("Invalid image centering flag"); return false; }
        if (u(b,7)==1) {
          x += (bitmap.getWidth()-image.getWidth())/2;
          y += (bitmap.getHeight()-image.getHeight())/2;
        }
        p.setStyle(Paint.Style.FILL); p.setFilterBitmap(false);
        target.drawBitmap(image,x,y,p);
        renderedImages++;
        String imageKey=ThemeImages.recordKey(b);
        if(imageKey!=null && imageKey.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) gifPreviews++;
        partial=true; // GIF first-frame preview; stock sends no animation-frame clock.
        break;
      case 20:
        p.setColor(color(u(b, 9)));
        target.drawLine(x, y, x, y + u(b, 7), p);
        break;
      case 21:
        p.setColor(color(u(b, 9)));
        target.drawLine(x, y, x + u(b, 7), y, p);
        break;
      default:
        reject();
        return false;
    }
    contentVersion++;
    if (!gameBatch) gameContent = false;
    lastFrame = android.os.SystemClock.elapsedRealtime();
    setContentDescription(
        "Received Bruce screen. Approximate rendering; text fonts and images may differ.");
    if (!gameBatch) postInvalidateOnAnimation();
    return true;
  }

  private void reject() {
    reject("Invalid or unsupported draw, geometry or rate limit");
  }
  private void reject(String reason) {
    partial = true;
    rejected++;
    totalRejected++;
    lastRejection = reason;
  }

  @Override
  protected void onMeasure(int width, int height) {
    int w = MeasureSpec.getSize(width);
    float density = getResources().getDisplayMetrics().density;
    // Reserve room for connection, actions, rotary controls and app navigation.
    // Small phones need a smaller preview; the source bitmap remains unchanged.
    getWindowVisibleDisplayFrame(windowFrame);
    int availableHeight = windowFrame.height() > 0 ? windowFrame.height()
        : getResources().getDisplayMetrics().heightPixels;
    int previewLimit = Math.max(Math.round(96 * density), availableHeight - Math.round(488 * density));
    int h =
        bitmap == null
            ? Math.round(96 * density)
            : Math.min(previewLimit, Math.round(w * (float) bitmap.getHeight() / bitmap.getWidth()));
    // An unavailable screen should not push the actual controls below the fold.
    // Respect an exact parent size in renderer fixtures and embedded layouts.
    setMeasuredDimension(resolveSize(w, width), resolveSize(Math.min(h, 800), height));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawColor(Color.BLACK);
    Paint p = viewPaint;
    p.reset();
    if (bitmap == null) {
      p.setColor(0xffbab0c0);
      p.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
      p.setTextAlign(Paint.Align.CENTER);
      c.drawText("No screen received", getWidth() / 2f, getHeight() / 2f, p);
    } else {
      float scale =
          Math.min(
              getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
      float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
      displayBounds.set(
          (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
      p.setFilterBitmap(false);
      c.drawBitmap(bitmap, null, displayBounds, p);
      if (lastFrame > 0 && contentVersion != presentedVersion) {
        presentedVersion = contentVersion;
        presentedUpdates++;
        lastPresentedAt = android.os.SystemClock.elapsedRealtime();
        if (gameContent) presentedGameFrame = gameFramesComposed;
      }
    }
  }
}
