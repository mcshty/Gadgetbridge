package nodomain.freeyourgadget.gadgetbridge.activities;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;

import nodomain.freeyourgadget.gadgetbridge.R;

public class DashboardFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View dashboardView = inflater.inflate(R.layout.fragment_dashboard, container, false);
        setHasOptionsMenu(true);

        PieChart chart = dashboardView.findViewById(R.id.dashboard_piechart_today);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.argb(0,0,0,0));
        chart.setHoleRadius(80f);
        chart.setRotationEnabled(false);
        chart.setDrawEntryLabels(false);

        Legend l = chart.getLegend();
        ArrayList<LegendEntry> legendEntries = new ArrayList<>();
        legendEntries.add(new LegendEntry("Deep sleep", Legend.LegendForm.SQUARE, 10f, 10f, new DashPathEffect(new float[]{10f, 5f}, 0f), Color.rgb(0, 0, 255)));
        legendEntries.add(new LegendEntry("Light sleep", Legend.LegendForm.SQUARE, 10f, 10f, new DashPathEffect(new float[]{10f, 5f}, 0f), Color.rgb(150, 150, 255)));
        legendEntries.add(new LegendEntry("Inactive", Legend.LegendForm.SQUARE, 10f, 10f, new DashPathEffect(new float[]{10f, 5f}, 0f), Color.rgb(200, 200, 200)));
        legendEntries.add(new LegendEntry("Active", Legend.LegendForm.SQUARE, 10f, 10f, new DashPathEffect(new float[]{10f, 5f}, 0f), Color.rgb(0, 255, 0)));
        l.setCustom(legendEntries);

        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        entries.add(new PieEntry(120, "Deep sleep"));
        colors.add(Color.rgb(0, 0, 255));
        entries.add(new PieEntry(120, "Light sleep"));
        colors.add(Color.rgb(150, 150, 255));
        entries.add(new PieEntry(60, "Deep sleep"));
        colors.add(Color.rgb(0, 0, 255));
        entries.add(new PieEntry(120, "Light sleep"));
        colors.add(Color.rgb(150, 150, 255));
        entries.add(new PieEntry(180, "Inactive"));
        colors.add(Color.rgb(200, 200, 200));
        entries.add(new PieEntry(60, "Active"));
        colors.add(Color.rgb(0, 255, 0));
        entries.add(new PieEntry(60, "Inactive"));
        colors.add(Color.rgb(200, 200, 200));
        entries.add(new PieEntry(30, "Active"));
        colors.add(Color.rgb(0, 255, 0));
        entries.add(new PieEntry(150, "Inactive"));
        colors.add(Color.rgb(200, 200, 200));
        entries.add(new PieEntry(60, "Active"));
        colors.add(Color.rgb(0, 255, 0));
        entries.add(new PieEntry(60, "Inactive"));
        colors.add(Color.rgb(200, 200, 200));
        entries.add(new PieEntry(90, "Active"));
        colors.add(Color.rgb(0, 255, 0));
        entries.add(new PieEntry(150, "Inactive"));
        colors.add(Color.rgb(200, 200, 200));
        entries.add(new PieEntry(120, "Light sleep"));
        colors.add(Color.rgb(150, 150, 255));
        entries.add(new PieEntry(60, "Deep sleep"));
        colors.add(Color.rgb(0, 0, 255));

        PieDataSet dataSet = new PieDataSet(entries, "Today");
        dataSet.setSliceSpace(0f);
        dataSet.setSelectionShift(5f);
        dataSet.setDrawValues(false);
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        chart.setData(data);

        return dashboardView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.dashboard_menu, menu);
    }
}