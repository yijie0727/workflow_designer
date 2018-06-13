package cz.zcu.kiv.WorkflowDesigner.Visualizations.PlotlyGraphs;

import org.json.JSONArray;
import org.json.JSONObject;

public class Axis {
    double min,max;

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public JSONObject toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("range",new JSONArray(new double[]{getMin(),getMax()}));
        return jsonObject;
    }
}
