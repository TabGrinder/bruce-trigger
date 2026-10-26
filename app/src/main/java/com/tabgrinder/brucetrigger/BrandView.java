package com.tabgrinder.brucetrigger;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Accessible two-colour wordmark, drawn with the already licensed classic pixel glyphs. */
final class BrandView extends View {
  private final Paint paint=new Paint();
  private static final String TITLE="Bruce trigger";
  BrandView(Context context){super(context);setContentDescription(TITLE);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
  @Override protected void onMeasure(int width,int height){
    float d=getResources().getDisplayMetrics().density;
    setMeasuredDimension(resolveSize((int)(230*d),width),resolveSize((int)(29*d),height));
  }
  @Override protected void onDraw(Canvas canvas){
    float unit=Math.min(getWidth()/77f,getHeight()/9f),top=(getHeight()-7*unit)/2f;
    for(int i=0;i<TITLE.length();i++){
      paint.setColor(i<6?FusionTheme.TEXT:FusionTheme.LIME);
      for(int col=0;col<5;col++){
        int bits=StockGlyphs.column(TITLE.charAt(i),col);
        for(int row=0;row<8;row++)if((bits&(1<<row))!=0){
          float x=(i*6+col)*unit,y=top+row*unit;
          canvas.drawRect(x,y,x+unit,y+unit,paint);
        }
      }
    }
  }
}
