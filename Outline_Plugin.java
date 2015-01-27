import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import ij.plugin.filter.*;
import java.util.*;
import java.awt.Color;
import java.awt.Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RefineryUtilities;
import org.jfree.ui.TextAnchor;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Outline_Plugin implements PlugInFilter, DialogListener, MouseListener {
    int dist_from_background;
    int width;
    int height;
    int depth;
    int pixelArea;
    int threshVal;
    
    int[] unalteredImage;
    int[] image;
    int[] pixels;
    
    ArrayList<Integer> inside = new ArrayList<Integer>();
    ArrayList<Integer> outside = new ArrayList<Integer>();
    
    boolean getInside = false;
    boolean getOutside = false;
    
    LinkedList<Integer> curve = new LinkedList<Integer>();
    GenericDialog gd;
    ImagePlus imp;
    ImageProcessor ip;
    ImageCanvas canvas;
    
    ImagePlus histogram;
    

public int setup(String arg, ImagePlus imp) {
    if(imp == null){
        IJ.showMessage("Error", "This plugin requires an image.");
        return -1;
    }
    ImageConverter a = new ImageConverter(imp);
    a.convertToRGB();
    this.imp = imp;
	return DOES_RGB;
}

public void run(ImageProcessor ip) {
    ImageWindow win = imp.getWindow();
    canvas = win.getCanvas();
    canvas.addMouseListener(this);
    
    this.ip = ip;
    this.dist_from_background = 30;
    this.image = (int[])ip.getPixels();
    unalteredImage = new int[this.image.length];
    copy(this.image, this.unalteredImage);
    this.width = ip.getWidth();
    this.height = ip.getHeight();
    this.pixels = new int[this.unalteredImage.length];
    copy(this.unalteredImage, pixels);

    this.getInside = true;
    NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Click Inside");
    this.gd = gd;
    gd.hideCancelButton();
    gd.addMessage("Click inside the the breast");
    gd.centerDialog(false);
    gd.showDialog();

    
    if (gd.wasCanceled()) {
        ip.setPixels(this.unalteredImage);
        imp.updateAndDraw();
        
        
    } else if(gd.wasOKed()){
        this.getInside = false;
        this.getOutside = true;
        NonBlockingGenericDialog gd2 = new NonBlockingGenericDialog("Click Outside");
        this.gd = gd2;
        gd2.hideCancelButton();
        gd2.addMessage("Click outside the the breast");
        gd2.centerDialog(false);
        gd2.showDialog();
        
        ip.setPixels(this.unalteredImage);
        imp.updateAndDraw();
        
        if (gd2.wasCanceled()) {
            ip.setPixels(this.unalteredImage);
            imp.updateAndDraw();
            
            
        } else if(gd2.wasOKed()){
            int insideVal = sum(this.inside);
            int outsideVal = sum(this.outside);
            if(outsideVal == 0) outsideVal = 1;
            int toThresh = insideVal / outsideVal;
            this.thresh(toThresh);
            ip.setPixels(this.pixels);
        }
    }
	
}
    
    public int sum(ArrayList<Integer> toSum){
        int toReturn = 0;
        for(Integer i : toSum){
            toReturn += pixels[i];
        }
        return toReturn;
    }
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e){
        // Not sure if needed
        return true;
    }
    
    
    public void mainRun(){
            this.pixels = new int[this.unalteredImage.length];
            copy(this.unalteredImage, pixels);
            thresh(this.threshVal);
            open(10);
            close(10);
            ip.setPixels(this.image);
            
            int area = getArea();
            int volume = getVolume(area);
            imp.updateAndDraw();
    
        }
    
      

public int getArea(){
    int count = 0;
    int offset, i;
    int[] cpy = new int[pixels.length];
    copy(pixels, cpy);
    for (int y=0; y<this.height; y++) {
        offset = y*width;
        for (int x=0; x<this.width; x++) {
            i = offset + x;
            if(getRed(pixels[i])==255){
                count ++;
            }
        }
    }
    return this.pixelArea*count;
}

    
public int getVolume(int area){
    return this.depth * area;
}
 



public void drawCurve(){
    this.copy(this.unalteredImage, this.image);
    for(Integer i : curve){
        setRGB(image, i.intValue(), 255, 255, 0);
    }
}
    
    
// Set the rgb values of location i
public void setRGB(int[] pic, int i, int r, int g, int b){
    pic[i] = ((r & 0xff)<<16)+((g & 0xff)<<8) + (b & 0xff);
}

// Get the grayscale value of a value by averaging the RGB values
public int getGray(int x, int y){
    int[] rgb = new int[3];
    ip.getPixel(x,y,rgb);
    
    return (rgb[0] + rgb[1] + rgb[2])/3;
}

// Get the red value from an int
public int getRed(int val){
    return (int)(val & 0xff0000)>>16;
}
    
// Create a binary image by thresholding the image using the given threshold value
public void thresh(int threshVar){
	int offset, i;
	for (int y=0; y < this.height; y++) {
        offset = y*width;
		for (int x=0; x < this.width; x++) {
            i = offset + x;
            int grayVal = getGray(x,y);
			if(grayVal > threshVar && x > 0 && x < width-1 && y > 0 && y < height-1){
				setRGB(pixels, i, 255, 0, 0);
			} else {
				setRGB(pixels, i, 0, 0, 0);
			}
		}
    }
}
    
    // copy the values from src into destinatiion
    public void copy(int[] src, int[] destination){
        int length = src.length;
        for(int i = 0; i < length; i++){
            destination[i] = src[i];
        }
    }

    // Erode iter number of times and then dilate iter number of times
    public void open(int iter){
        for(int i = 0; i < iter; i++){
            erode();
        }
        for(int j = 0; j < iter; j++){
            dilate();
        }
    }
    
    // Dilate iter number of times then erode iter number of times
    public void close(int iter){
        for(int i = 0; i < iter; i++){
            dilate();
        }
        for(int j = 0; j < iter; j++){
            erode();
        }
    }
    
    // find highlighted pixels touching the background and turn them to the background color
    public void erode(){
        int offset, i;
        int[] cpy = new int[pixels.length];
        copy(pixels, cpy);
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                i = offset + x;
                if(y > 0 && y < height && x > 0 && x < width && getRed(pixels[i])==255){
                    if(connectedBlack(pixels, i, x, y)){
                        setRGB(cpy, i, 0,0,0);
                    }
                }
            }
        }
        copy(cpy, pixels);
    }
    
    // find background pixels touching the lesion structure and add them to the lesion
    public void dilate(){
        int offset, i;
        int[] cpy = new int[pixels.length];
        copy(pixels, cpy);
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                    i = offset + x;
                    if(y > 0 && y < height && x > 0 && x < width && getRed(pixels[i])==0){
                        if(connectedRed(pixels, i, x, y)){
                            setRGB(cpy, i, 255,0,0);
                    }
                }
            }
        }
        copy(cpy, pixels);
    }

    // Check if a pixel is connected to a black component
    public boolean connectedBlack(int[] pic, int i, int x, int y){
        int up = (y+1)*width;
        int down = (y-1)*width;
        if(up+x > pic.length){
            if(i == pic.length-1){
                if (getRed(pic[i-1]) == 0 || getRed(pic[down+x]) == 0)
                    return true;
                return false;
            } else if (getRed(pic[i+1]) == 255 || getRed(pic[i-1]) == 255 || getRed(pic[down+x]) == 255)
                return true;
            return false;
        }
        
        if (getRed(pic[i+1]) == 0 || getRed(pic[i-1]) == 0 || getRed(pic[up+x]) == 0 || getRed(pic[down+x]) == 0){
            return true;
        } else {
            return false;
        }
    }
    
    // Check if a pixel is connected to a red component
    public boolean connectedRed(int[] pic, int i, int x, int y){
        int up = (y+1)*width;
        int down = (y-1)*width;
        if(up+x > pic.length){
            if(i == pic.length-1){
                if (getRed(pic[i-1]) == 255 || getRed(pic[down+x]) == 255)
                    return true;
                return false;
                
            }
            else if (getRed(pic[i+1]) == 255 || getRed(pic[i-1]) == 255 || getRed(pic[down+x]) == 255)
                return true;
            return false;
        }
        
        if (getRed(pic[i+1]) == 255 || getRed(pic[i-1]) == 255 || getRed(pic[up+x]) == 255 || getRed(pic[down+x]) == 255){
            return true;
        } else {
            return false;
        }
    }

    
    public void mouseClicked(MouseEvent e) {
        if(this.getInside){
            int x = e.getX();
            int y = e.getY();
            int width = y * this.width;
            this.inside.add(width + x);
        } else if (this.getOutside){
            int x = e.getX();
            int y = e.getY();
            int width = y * this.width;
            this.outside.add(width + x);
        }


    }
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    
    
}
