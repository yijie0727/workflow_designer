package cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;

public class Layout implements Serializable {
    String title;
    Axis xaxis;
    Axis yaxis;



    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Axis getXaxis() {
        return xaxis;
    }

    public void setXaxis(Axis xaxis) {
        this.xaxis = xaxis;
    }

    public Axis getYaxis() {
        return yaxis;
    }

    public void setYaxis(Axis yaxis) {
        this.yaxis = yaxis;
    }

    public JSONObject toJSON(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title",getTitle());
        jsonObject.put("xaxis",getXaxis().toJSON());
        jsonObject.put("yaxis",getYaxis().toJSON());
        return jsonObject;
    };
}
