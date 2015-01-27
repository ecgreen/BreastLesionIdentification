import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import ij.plugin.filter.*;
import ij.measure.*;
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
import java.awt.Point;
import java.util.Collections;


public class Breast_Volume_Plugin implements PlugInFilter, DialogListener, MouseListener {
    int dist_from_background;
    int width;
    int height;
    int depth;
    int pixelArea;
    int threshVal;
    int calcThresh;
	
	ArrayList<Integer> xCor = new ArrayList<Integer>();
	ArrayList<Integer> yCor = new ArrayList<Integer>();
	boolean splineStarted = false;
	
	boolean right = false;
	boolean roi = false;
	PolygonRoi fproi;
    
    int[] unalteredImage;
    int[] image;
    int[] pixels;
    
    int[] hist;
    
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

	
	
// Setup the plugin.
public void run(ImageProcessor ip) {
    ImageWindow win = imp.getWindow();
    canvas = win.getCanvas();
    canvas.addMouseListener(this);
    
    this.ip = ip;
    this.dist_from_background = 2;
    this.image = (int[])ip.getPixels();
    unalteredImage = new int[this.image.length];
    copy(this.image, this.unalteredImage);
    this.width = ip.getWidth();
    this.height = ip.getHeight();
    this.pixels = new int[this.unalteredImage.length];
    copy(this.unalteredImage, pixels);
    updateHist();
    this.threshVal = getThresh();
    this.calcThresh = this.threshVal;
    
    Button b = new Button("Update");
    b.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e)
        { mainRun(); }
    });
    
    Button b2 = new Button("Auto");
    b2.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e)
        {   auto(); mainRun(); }
    });

	
	Button b3 = new Button("Remove ROI");
    b3.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e)
		{removeROI(); }
	});
    
    NonBlockingGenericDialog gd = new NonBlockingGenericDialog("Lesion Detection");
    this.gd = gd;
    gd.addNumericField("Area Per Pixel:",1.0,1);
    gd.addNumericField("Tumor Depth:",1.0,1);
    gd.addSlider("Threshold Value:", 0, 255, this.threshVal);
    gd.addDialogListener( (DialogListener) this);
    gd.hideCancelButton();
    gd.add(b);
    gd.add(b2);
	gd.add(b3);
    gd.addMessage("Area:     Volume:");
    gd.centerDialog(false);
    gd.addDialogListener(this);
    
    
    mainRun();
    gd.showDialog();


    
    if (gd.wasCanceled()) {
		canvas.removeMouseListener(this);
        ip.setPixels(this.unalteredImage);
        imp.updateAndDraw();
        if(histogram.getWindow() != null) histogram.close();
        
        
    } else if(gd.wasOKed()){

        ip.setPixels(this.unalteredImage);
        imp.updateAndDraw();
        if(histogram.getWindow() != null) histogram.close();
    }
	
}
	
	
	
	
	
	
	
	
	
	
	
	// The auto button method.
    public void auto(){
        this.threshVal = getThresh();
        this.calcThresh = this.threshVal;
        ((Scrollbar) gd.getSliders().get(0)).setValue(this.threshVal);
        ((TextComponent)gd.getNumericFields().get(2)).setText(""+this.threshVal);

    }
	
	
	
	
	
	
    // What happens when a user updates something in the dialog
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e){
        int tempPixelArea = (int) this.gd.getNextNumber();
        int tempDepth = (int) this.gd.getNextNumber();
        int tempThresh = (int) this.gd.getNextNumber();
        if(this.pixelArea != tempPixelArea || this.depth != tempDepth){
            this.pixelArea = tempPixelArea;
            this.depth = tempDepth;
            
            int area = getArea();
            int volume = getVolume(area);
            ((Label)gd.getMessage()).setText("Area: " + area + "      Volume: " + volume);
        }
        if(this.threshVal != tempThresh){
            this.threshVal = tempThresh;
            if(histogram.getWindow() != null)
                generateHistogram(this.hist, this.threshVal);
        }
        return true;
    }
	
	
	
	
	
	
	
	
	public void displaySpline(SplineFitter sf){
			// eval spline returns the x value for a given y value
		for(int i = 0; i < this.height; i++){
			int xcor = (int) sf.evalSpline(i);
			if(xcor >= 0 && xcor < this.width){
				int ival = i*this.width + xcor;
				setRGB(this.image, ival, 0, 255, 0);
			}
		}
	}
    
  
	
	/**
	 *	The main method of this program. This is where the beast outline is calculated, the area is calculated
	 *	and everything is updated.
	 */
    public void mainRun(){
        curve.clear();
        this.pixels = new int[this.unalteredImage.length];
        copy(this.unalteredImage, pixels);
        
		
        // Generate a thresh image, find the largest connected components, reset the image to only use the largest.
        thresh(this.threshVal);
		
		
        ArrayList<Integer> largest = largestConnected();
        setLargest(largest);
            
        
        updateHist();
        generateHistogram(this.hist, this.threshVal);
        
		int area = getArea();
        int volume = getVolume(area);
		
		getCurve();
        drawCurve();
        ip.setPixels(this.image);
            

        ((Label)gd.getMessage()).setText("Area: " + area + "      Volume: " + volume);
            
        imp.updateAndDraw();
    
        }

 
	
	
	
	
	
	// Updates the current state of the histogram
    public void updateHist(){
        int hist[] = new int[256];
        int offset, i;
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                    i = offset + x;
                    int val = getGray(x,y);
                    hist[val]++;
            }
        }
        this.hist = hist;
    }

	
	
	
	

// THis method calculates the threshold over the image    
public int getThresh(){

    // find background (max 1) and the max image color (max 2)
    int max1 = 0;
    int max1loc = 0;
    int max2 = 0;
    int max2loc = 255;
    for(int k=0; k < 256; k++){
        if(hist[k] > max1 && k < 100){
            max1 = hist[k];
            max1loc = k;
        }
        if(hist[k] > max2 && hist[k] < max1 && (k-max1loc)> dist_from_background && k < 100){
            max2 = hist[k];
            max2loc = k;
        }
    }
    // find the min between max1 and max2
    int min = max1;
    int minLoc = max1loc;
    for(int i = max1loc+1; i < max2loc; i++){
        if(min > hist[i]){
            min = hist[i];
            minLoc = i;
        }
    }
    generateHistogram(hist, minLoc);
    return minLoc;
    
}
    
    
    // Generates a histogram image.
    public void generateHistogram(int[] hist, int thresh){
       
        final XYSeries series1 = new XYSeries("Stuff");
        for(int i = 0; i < hist.length; i++){
            if(i != thresh && i != this.calcThresh){
                series1.add(i, hist[i]);
            }
        }
        // Add the thresh val to series 2
        final XYSeries series2 = new XYSeries("Current Threshold");
        series2.add(thresh, hist[thresh]);
        
        // Add the calculated thresh val to series 3
        final XYSeries series3 = new XYSeries("Auto Threshold");
        series3.add(this.calcThresh, hist[this.calcThresh]);
        
        //Create the series collection with series 1, 2, and 3
        final XYSeriesCollection dataset = new XYSeriesCollection(series1);
        dataset.addSeries(series2);
        dataset.addSeries(series3);
        
        final JFreeChart chart = ChartFactory.createXYBarChart( "Histogram", "Pixel Value", false, "# Of Occurrences", dataset, PlotOrientation.VERTICAL, true,true,false);
        if(this.histogram == null){
            this.histogram = IJ.createImage("Comparison", "RGB", 420, 420, 1);
        }
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.getRenderer().setSeriesPaint(0, Color.black);
        plot.getRenderer().setSeriesPaint(1, Color.RED);
        plot.getRenderer().setSeriesPaint(2, Color.yellow);
        XYBarRenderer renderer = (XYBarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardXYBarPainter());
        
        
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        
        BufferedImage image = histogram.getBufferedImage();
        chart.draw(image.createGraphics(), new Rectangle2D.Float(0, 0, 400, 400));
        histogram.setImage(image);
        histogram.show();
    }


public int getArea(){
    int count = 0;
    int offset, i;
    int[] cpy = new int[pixels.length];
    copy(pixels, cpy);
    for (int y=0; y<this.height; y++) {
        offset = y*width;
        for (int x=0; x<this.width; x++) {
			
			// If the roi has been set
			if(roi) {
				// Check if the roi contains the point
				if(! this.fproi.contains(x,y)){
					i = offset + x;
					if(getRed(pixels[i])==255){
						count ++;
					}
				}
				
			} else {
				i = offset + x;
				if(getRed(pixels[i])==255){
					count ++;
				}
            }
        }
    }
    return this.pixelArea*count;
}

    
public int getVolume(int area){
    return this.depth * area;
}
 

// Finds pixels on the edge between the lesion center and the background
public void getCurve(){
    int offset, i;
    int[] cpy = new int[pixels.length];
    copy(pixels, cpy);
    for (int y=0; y<this.height; y++) {
        offset = y*width;
        for (int x=0; x<this.width; x++) {
            i = offset + x;
            if(y > 0 && y < this.height && x > 0 && x < this.width){
                if(getRed(pixels[i])==255){
                    if(connectedAllRed(pixels, i, x, y)){
                        setRGB(cpy, i, 0,0,0);
                    } else {
                        // This is part of the iso curve (that doesn't use an iso value so really just a curve)
                        this.curve.add(i);
                    }
                }
            }
        }
    }
    
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
    
    public void setLargest(ArrayList<Integer> largest){
        this.pixels = new int[this.pixels.length];
        for(Integer i : largest) {
            setRGB(pixels, i, 255, 0, 0);
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
 
// copy the values from src into destinatiion
public void copy(int[] src, int[] destination){
    int length = src.length;
    for(int i = 0; i < length; i++){
        destination[i] = src[i];
    }
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

// Check if a pixel location is connected to all red
public boolean connectedAllRed(int[] pic, int i, int x, int y){
    int up = (y+1)*width;
    int down = (y-1)*width;
    
    if(up+x > pic.length){
        if(i == pic.length-1){
            if (getRed(pic[i-1]) == 255 && getRed(pic[down+x]) == 255)
                return true;
            return false;
            
        }
        else if (getRed(pic[i+1]) == 255 && getRed(pic[i-1]) == 255 && getRed(pic[down+x]) == 255)
            return true;
        return false;
    }
    
    if (getRed(pic[i+1]) == 255 && getRed(pic[i-1]) == 255 && getRed(pic[up+x]) == 255 && getRed(pic[down+x]) == 255)
        return true;
    return false;
}

    
    public ArrayList<Integer> largestConnected(){
        int offset, i;
        int label = 1;
        
        // Array for checking neighbor labels
        int[] labels = new int[this.unalteredImage.length];
        
        // Label consolidation array
        ArrayList<Integer> labelEqual = new ArrayList<Integer>();
        labelEqual.add(label);
        
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                i = offset + x;
                if(pixels[i] != 0) {
                    
                    boolean labelset = false;
                    
                    if(y > 0){
                        if(labels[i-this.width] != 0){
                            labels[i] = labels[i-this.width];
                            labelset = true;
                        }
                    }
                    if(x > 0){
                        if(labels[i-1] != 0){
                            if(labelset){
                                labels[i] = Math.min(labels[i-1], labels[i-this.width]);
                                
                                if(labels[i-1] > labels[i-this.width]){
                                    labelEqual.set(labels[i-1]-1, Math.min(labels[i-this.width], labelEqual.get(labels[i-1]-1)));
                                } else {
                                    labelEqual.set(labels[i-this.width]-1, Math.min(labels[i-1], labelEqual.get(labels[i-this.width]-1)));
                                }
								
                            } else {
                                labels[i] = labels[i-1];
                                labelset = true;
                            }
                        }
                    }
                    if(!labelset){
                        labels[i] = label;
                        label++;
                        labelEqual.add(label);
                    }
                    
                }
            }
        }
        
        // minimize the label components
        for(int j = 2; j < labelEqual.size(); j++){
            labelEqual.set(j, labelEqual.get(labelEqual.get(j)-1));
        }

        // Consolidate all lables. Track the size of each label
        int[] labelSizes = new int[label+1];
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                i = offset + x;
                if(pixels[i] != 0){
                    labels[i] = labelEqual.get(labels[i]-1);
                    labelSizes[labels[i]]++;
                }
            }
        }
        
        // Find max label size
        int maxLabelCount = 0;
        int maxLabel = 0;
        for(int j = 0; j < labelSizes.length; j++){
            if(labelSizes[j] > maxLabelCount){
                maxLabelCount = labelSizes[j];
                maxLabel = j;
            }
        }
        
        // Create an arraylist of the locations of the largest connected component object
        ArrayList<Integer> toReturn = new ArrayList<Integer>();
        
        for (int y=0; y<this.height; y++) {
            offset = y*width;
            for (int x=0; x<this.width; x++) {
                i = offset + x;
                if(labels[i] == maxLabel){
                    toReturn.add(i);
                }
            }
        }
        
        return toReturn;

    }
	
	public float[] castFloatArray(ArrayList<Integer> a) {
		float[] toReturn = new float[a.size()];
		for(int i = 0; i < a.size(); i++){
			toReturn[i] = new Float(a.get(i));
		}
		return toReturn;
	}
	
	public int[] getIntArray(ArrayList<Integer> a){
		int[] toReturn = new int[a.size()];
		for(int i = 0; i < a.size(); i++){
			toReturn[i] = a.get(i);
		}
		return toReturn;
	}
    
	public void addSorted(int x, int y){
		// Check to see if the lists are empty
		if(yCor.size() != 0){
			// Delete the extra points on the ends
			yCor.remove(yCor.size()-1);
			yCor.remove(0);
			xCor.remove(xCor.size()-1);
			xCor.remove(0);
		}
		
		yCor.add(y);
		Collections.sort(yCor);
		int sortedIndex = yCor.indexOf(y);
		xCor.add(sortedIndex, x);
		
		if(this.right){
			yCor.add(yCor.get(yCor.size()-1));
			yCor.add(0, yCor.get(0));
			xCor.add(this.width-1);
			xCor.add(0, this.width-1);
		} else {
			yCor.add(yCor.get(yCor.size()-1));
			yCor.add(0, yCor.get(0));
			xCor.add(0);
			xCor.add(0, 0);
		}
		
	}
	
	public void removeROI(){
		yCor.clear();
		xCor.clear();
		this.right = false;
		this.roi = false;
		this.imp.deleteRoi();
		
		int area = getArea();
		int volume = getVolume(area);
		((Label)gd.getMessage()).setText("Area: " + area + "      Volume: " + volume);
	}
	
	public void undo(){
		
	}
	
	public void mouseClicked(MouseEvent e) { 
		// Check to see which side has the muscle
		Point p = canvas.getCursorLoc();
		if(yCor.size() == 0){
			if(p.x > this.width/2){
				this.right = true;
			}
		}
		
		addSorted(p.x, p.y);
		

		PolygonRoi pr = new PolygonRoi(getIntArray(xCor), getIntArray(yCor), xCor.size(), Roi.POLYLINE);
		pr.fitSpline();
		
		this.fproi = pr;
			
		// Calculate area using the float polygon
		imp.setRoi(pr);
		if(yCor.size() > 3) {
			this.roi = true;
			int area = getArea();
			int volume = getVolume(area);
			((Label)gd.getMessage()).setText("Area: " + area + "      Volume: " + volume);
		}
	}
    
	public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
	
    
}
