package cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs;

import org.json.JSONArray;
import org.json.JSONObject;

public class Layout {
    String title;

    double xAxisMin,xAxisMax,yAxisMin,yAxisMax;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public double getxAxisMin() {
        return xAxisMin;
    }

    public void setxAxisMin(double xAxisMin) {
        this.xAxisMin = xAxisMin;
    }

    public double getxAxisMax() {
        return xAxisMax;
    }

    public void setxAxisMax(double xAxisMax) {
        this.xAxisMax = xAxisMax;
    }

    public double getyAxisMin() {
        return yAxisMin;
    }

    public void setyAxisMin(double yAxisMin) {
        this.yAxisMin = yAxisMin;
    }

    public double getyAxisMax() {
        return yAxisMax;
    }

    public void setyAxisMax(double yAxisMax) {
        this.yAxisMax = yAxisMax;
    }

    public JSONObject toJSON(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title",getTitle());
        jsonObject.put("xaxis",new JSONArray(new double[]{getxAxisMin(),getxAxisMax()}));
        jsonObject.put("yaxis",new JSONArray(new double[]{getyAxisMin(),getyAxisMax()}));
        return jsonObject;
    };
}
