package cz.zcu.kiv.WorkflowDesigner;

import java.util.ArrayList;

public class Table {
    private ArrayList<String> columnHeaders;
    private ArrayList<String> rowHeaders;
    private ArrayList<ArrayList<String>>rows;

    public ArrayList<String> getColumnHeaders() {
        return columnHeaders ;
    }

    public void setColumnHeaders(ArrayList<String> columnHeaders) {
        this.columnHeaders = columnHeaders;
    }

    public ArrayList<String> getRowHeaders() {
        return rowHeaders;
    }

    public void setRowHeaders(ArrayList<String> rowHeaders) {
        this.rowHeaders = rowHeaders;
    }

    public ArrayList<ArrayList<String>> getRows() {
        return rows;
    }

    public void setRows(ArrayList<ArrayList<String>> rows) {
        this.rows = rows;
    }

    public String getHTML(){
        StringBuilder html=new StringBuilder();
        html.append("<table border=\"1\">\n");
        boolean hasColumnHeaders = getColumnHeaders().size()>0;
        boolean hasRowHeaders = getRowHeaders().size()>0;
        if(hasColumnHeaders){
            html.append("<tr>");
            if(hasRowHeaders){
                html.append("<th></th>");
            }
            for(String header:columnHeaders){
                html.append("<th>"+header+"</th>");
            }
            html.append("</tr>\n");
        }

        for(int i=0;i<rows.size();i++){
            html.append("<tr>");
            if(hasRowHeaders){
                html.append("<th>"+getRowHeaders().get(i)+"</th>");
            }
            for(String col:rows.get(i)){
                html.append("<td>"+col+"</td>");
            }
            html.append("</tr>\n");
        }
        html.append("</table>");
        return html.toString();
    }
}
