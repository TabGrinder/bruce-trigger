package com.tabgrinder.brucetrigger;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/** Eight bounded RGB565 sprites. Copy on first mutation makes a frame transactional. */
final class GameSprites {
  private Bitmap[] images=new Bitmap[9], before;
  private final Bitmap[] spare=new Bitmap[9];
  private final Canvas copyCanvas=new Canvas();
  private long allocations,reuses;
  private final Bitmap[] cachedSource=new Bitmap[9], transparentImages=new Bitmap[9];
  private final int[] cachedColor=new int[9];
  private int[] pixels;
  private final android.graphics.Rect source=new android.graphics.Rect();
  private final android.graphics.Rect destination=new android.graphics.Rect();
  private int selected, previous;
  void begin(){if(before!=null)throw new IllegalStateException("Sprite transaction");before=images.clone();previous=selected;}
  int selected(){return selected;}
  void select(int id){if(id<0||id>8||id!=0&&images[id]==null)throw new IllegalArgumentException("Unknown sprite");selected=id;}
  Bitmap writable() {
    if(selected==0)return null;
    Bitmap b=images[selected];
    if(b==null||before==null)throw new IllegalStateException("Sprite target");
    if(b==before[selected]) {
      Bitmap next=spare[selected];spare[selected]=null;
      if(next!=null && (next.getWidth()!=b.getWidth() || next.getHeight()!=b.getHeight())){next.recycle();next=null;}
      if(next==null){next=b.copy(Bitmap.Config.RGB_565,true);allocations++;}
      else {copyCanvas.setBitmap(next);copyCanvas.drawBitmap(b,0,0,null);copyCanvas.setBitmap(null);reuses++;}
      images[selected]=b=next;
    }
    if(b==null)throw new IllegalStateException("Sprite allocation");
    cachedSource[selected]=null;
    return b;
  }
  void create(int id,int w,int h) {
    if(before==null||id<1||id>8||images[id]!=null||w<1||w>320||h<1||h>170)throw new IllegalArgumentException("Sprite bounds");
    Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);b.eraseColor(android.graphics.Color.BLACK);images[id]=b;
  }
  void delete(int id) {
    if(before==null||id<1||id>8||images[id]==null)throw new IllegalArgumentException("Unknown sprite");
    if(images[id]!=before[id])retain(id,images[id]);images[id]=null;
    if(selected==id)selected=0;
  }
  int area(int id){if(id<1||id>8||images[id]==null)throw new IllegalArgumentException("Unknown sprite");return images[id].getWidth()*images[id].getHeight();}
  void push(Canvas screen,int id,int x,int y,int transparent) {
    if(id<1||id>8||images[id]==null)throw new IllegalArgumentException("Unknown sprite");
    Bitmap sprite=images[id];int w=sprite.getWidth(),h=sprite.getHeight();
    if(cachedSource[id]!=sprite || cachedColor[id]!=transparent) {
      if(pixels==null)pixels=new int[320*170];
      sprite.getPixels(pixels,0,w,0,0,w,h);
      boolean hasTransparent=false;
      for(int i=0;i<w*h;i++) {
        int c=pixels[i],rgb=((c>>19&31)<<11)|((c>>10&63)<<5)|(c>>3&31);
        if(rgb==transparent){pixels[i]=0;hasTransparent=true;}
      }
      if(hasTransparent) {
        if(transparentImages[id]==null || transparentImages[id].getWidth()!=w || transparentImages[id].getHeight()!=h) {
          clearCache(id);
          transparentImages[id]=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        }
        transparentImages[id].setPixels(pixels,0,w,0,0,w,h);
      } else clearCache(id);
      cachedSource[id]=sprite;cachedColor[id]=transparent;
    }
    source.set(0,0,w,h);destination.set(x,y,x+w,y+h);
    screen.drawBitmap(transparentImages[id]==null?sprite:transparentImages[id],source,destination,null);
  }
  private void clearCache(int id) {
    if(transparentImages[id]!=null)transparentImages[id].recycle();
    transparentImages[id]=cachedSource[id]=null;
  }
  private void retain(int id,Bitmap value) {
    if(spare[id]!=null && spare[id]!=value)spare[id].recycle();
    spare[id]=value;
  }
  void finish(boolean commit) {
    if(before==null)return;
    for(int i=1;i<9;i++)if(images[i]!=before[i]) {
      Bitmap discard=commit?before[i]:images[i];if(discard!=null)retain(i,discard);
    }
    if(!commit){images=before;selected=previous;}
    for(int i=1;i<9;i++)if(cachedSource[i]!=images[i])clearCache(i);
    before=null;
  }
  void clear(){finish(false);for(int i=1;i<9;i++){if(images[i]!=null)images[i].recycle();images[i]=null;}
    for(int i=1;i<9;i++){clearCache(i);if(spare[i]!=null)spare[i].recycle();spare[i]=null;}
    copyCanvas.setBitmap(null);pixels=null;selected=0;}
}
