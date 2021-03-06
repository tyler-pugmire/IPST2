/******************************************************************************
 * Copyright 2017 Steven Foskett, Jimmy Ho, Ryan Porterfield                  *
 * Permission is hereby granted, free of charge, to any person obtaining a    *
 * copy of this software and associated documentation files (the "Software"), *
 * to deal in the Software without restriction, including without limitation  *
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,   *
 * and/or sell copies of the Software, and to permit persons to whom the      *
 * Software is furnished to do so, subject to the following conditions:       *
 *                                                                            *
 * The above copyright notice and this permission notice shall be included in *
 * all copies or substantial portions of the Software.                        *
 *                                                                            *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,   *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE*
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER     *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING    *
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER        *
 * DEALINGS IN THE SOFTWARE.                                                  *
 ******************************************************************************/

package com.einzig.ipst2.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.v7.widget.AppCompatDrawableManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.einzig.ipst2.R;
import com.einzig.ipst2.portal.PortalAccepted;
import com.einzig.ipst2.portal.PortalRejected;
import com.einzig.ipst2.portal.PortalSubmission;
import com.einzig.ipst2.util.Logger;
import com.einzig.ipst2.util.PreferencesHelper;

import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

// Custom list item class for menu items
public class ListItemAdapter_PS extends BaseAdapter implements Filterable {
    /**  */
    public ArrayList<? extends PortalSubmission> shownItems;
    /** Application context */
    private Context context;
    /**  */
    private Vector<? extends PortalSubmission> originalItems;
    /**  */
    private SubmissionFilter submissionFilter;

    public ListItemAdapter_PS(final Vector<? extends PortalSubmission> items, Context context) {
        this.context = context;
        this.originalItems = items;
        this.shownItems = new ArrayList<>(items);
    }

    public int getCount() {
        if (shownItems != null)
            return this.shownItems.size();
        else
            return 0;
    }

    @Override
    public Filter getFilter() {
        if (submissionFilter == null)
            submissionFilter = new SubmissionFilter();
        return submissionFilter;
    }

    public PortalSubmission getItem(int position) {
        return this.shownItems.get(position);
    }

    public long getItemId(int position) {
        return this.shownItems.get(position).hashCode();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final PortalSubmission item = this.shownItems.get(position);
        @SuppressLint("ViewHolder") LinearLayout itemLayout =
                (LinearLayout) LayoutInflater.from(context)
                        .inflate(R.layout.row_pslist, parent, false);
        ImageView iconView = (ImageView) itemLayout.findViewById(R.id.status_icon);
        if (iconView != null) {
            if (item instanceof PortalAccepted) {
                iconView.setImageDrawable(AppCompatDrawableManager.get().getDrawable(context, R
                        .drawable.ic_check));
                iconView.setBackgroundColor(context.getResources().getColor(R.color.accepted));
            } else if (item instanceof PortalRejected) {
                iconView.setImageDrawable(AppCompatDrawableManager.get().getDrawable(context, R
                        .drawable.ic_rejected));
                iconView.setBackgroundColor(context.getResources().getColor(R.color.rejected));
            } else {
                iconView.setImageDrawable(AppCompatDrawableManager.get().getDrawable(context, R
                        .drawable.ic_pending));
            }
        }

        TextView pstimelabel = (TextView) itemLayout.findViewById(R.id.psdate_rowpslist);
        if (pstimelabel != null) {
            DateTimeFormatter formatter = new PreferencesHelper(context).getUIFormatter();
            pstimelabel.setText(
                    formatter.print(item.getDateSubmitted()) + " - " + item
                            .getDaysSinceResponse() +
                            " day(s) ago");
        }
        TextView psnamelabel = (TextView) itemLayout.findViewById(R.id.psname_rowpslist);
        if (psnamelabel != null)
            psnamelabel.setText(item.getName());

        return itemLayout;
    }

    public void resetData() {
        this.shownItems = new ArrayList<>(originalItems);
        notifyDataSetChanged();
    }

    /*
     * Simple Sort class to sort ps by name
     * */
    public static class SortPortalSubmissions_alph implements Comparator<PortalSubmission> {
        @Override
        public int compare(PortalSubmission o1, PortalSubmission o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }

    /*
   * Simple Sort class to sort ps by date responded
   * */
    public static class SortPortalSubmissions_dateresp implements Comparator<PortalSubmission> {
        @Override
        public int compare(PortalSubmission o1, PortalSubmission o2) {
            int portal1int = o1.getDaysSinceResponse();
            int portal2int = o2.getDaysSinceResponse();

            if (portal1int > portal2int) {
                return 1;
            } else if (portal1int < portal2int) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    /*
     * Simple Sort class to sort ps by date submitted
     * */
    public static class SortPortalSubmissions_datesub implements Comparator<PortalSubmission> {
        @Override
        public int compare(PortalSubmission o1, PortalSubmission o2) {
            return o1.getDateSubmitted().compareTo(o2.getDateSubmitted());
        }
    }

    private class SubmissionFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            Logger.d("FILTERED: " + constraint);
            FilterResults results = new FilterResults();
            if (constraint == null || constraint.length() == 0) {
                results.values = new ArrayList<>(originalItems);
                results.count = originalItems.size();
            } else {
                List<PortalSubmission> nList = new ArrayList<>();
                for (PortalSubmission p : originalItems) {
                    if (p.getName().toUpperCase().contains(constraint.toString().toUpperCase()))
                        nList.add(p);
                }
                results.values = nList;
                results.count = nList.size();
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            try {
                ListItemAdapter_PS.this.shownItems =
                        (ArrayList<? extends PortalSubmission>) results.values;
                System.out.println("PUBLISHED: " + ListItemAdapter_PS.this.shownItems.size());
                notifyDataSetChanged();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}