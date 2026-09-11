package pro.sketchware.activities.preview;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ViewPane;
import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;

import a.a.a.jC;
import a.a.a.mB;
import pro.sketchware.databinding.ActivityLayoutPreviewBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class LayoutPreviewActivity extends BaseAppCompatActivity {
    private ViewPane pane;
    private String content;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        ActivityLayoutPreviewBinding binding = ActivityLayoutPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        var toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Layout Preview");
        getSupportActionBar().setSubtitle(getIntent().getStringExtra("title"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> { if (!mB.a()) onBackPressed(); });
        content = getIntent().getStringExtra("xml");
        var scId = getIntent().getStringExtra("sc_id");
        pane = binding.pane;
        pane.initialize(scId, true);
        pane.updateRootLayout(scId, getIntent().getStringExtra("title"));
        pane.setVerticalScrollBarEnabled(true);
        pane.setResourceManager(jC.d(scId));
        UI.addSystemWindowInsetToPadding(binding.pane, false, false, false, true);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (content == null || content.trim().isEmpty()) {
            showPreviewError("XML is empty.");
            return;
        }
        try {
            loadViews(new ViewBeanParser(content).parse());
        } catch (Exception e) {
            showPreviewError(cleanError(e));
        }
    }

    private ItemView loadView(ViewBean view) {
        var itemView = pane.createItemView(view);
        pane.addViewAndUpdateIndex(itemView);
        if (itemView instanceof ItemView sy) {
            sy.setFixed(true);
            return sy;
        }
        return null;
    }

    private ItemView loadViews(ArrayList<ViewBean> views) {
        ItemView root = null;
        for (int i = 0; i < views.size(); i++) {
            ViewBean view = views.get(i);
            if (i == 0) {
                view.parent = "root";
                view.parentType = 0;
                view.preParent = null;
                view.preParentType = -1;
                root = loadView(view);
            } else {
                loadView(view);
            }
        }
        return root;
    }

    private void showPreviewError(String message) {
        SketchwareUtil.toastError(message);
    }

    private String cleanError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "Invalid XML layout.";
        return message.replace("java.io.StringReader@", "XML error near ");
    }
}
