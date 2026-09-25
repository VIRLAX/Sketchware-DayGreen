// Replace occurrences in DesignDrawer.java
package com.besome.sketch.design;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.ui.EasyDeleteEditText;
import com.google.android.material.button.MaterialButton;

import a.a.a.mB;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import mod.hey.studios.activity.managers.nativelib.ManageNativelibsActivity;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;

public class DesignDrawer implements View.OnClickListener {

    private final DesignActivity designActivity;
    private final LinearLayout content;
    public FrameLayout drawerLayout;

    public DesignDrawer(DesignActivity designActivity, FrameLayout drawerLayout) {
        this.designActivity = designActivity;
        this.drawerLayout = drawerLayout;
        content = drawerLayout.findViewById(R.id.layout_drawer);
        init();
    }

    private void addDrawerItem(int id, int iconId, int titleId, int subtitleId, LinearLayout parent) {
        View view = LayoutInflater.from(designActivity).inflate(R.layout.custom_drawer_item, parent, false);
        view.setId(id);
        view.setOnClickListener(this);

        ImageView icon = view.findViewById(R.id.item_icon);
        icon.setImageResource(iconId);

        TextView title = view.findViewById(R.id.item_title);
        title.setText(titleId);

        TextView subtitle = view.findViewById(R.id.item_subtitle);
        subtitle.setText(subtitleId);

        parent.addView(view);
    }

    private void init() {
        content.removeAllViews();

        addDrawerItem(R.id.item_view_manager, R.drawable.ic_mtrl_view, R.string.design_drawer_menu_title_view, R.string.design_drawer_menu_description_view, content);
        addDrawerItem(R.id.item_image_manager, R.drawable.ic_mtrl_image, R.string.design_drawer_menu_title_image, R.string.design_drawer_menu_description_image, content);
        addDrawerItem(R.id.item_sound_manager, R.drawable.ic_mtrl_volume_up, R.string.design_drawer_menu_title_sound, R.string.design_drawer_menu_description_sound, content);
        addDrawerItem(R.id.item_font_manager, R.drawable.ic_mtrl_font, R.string.design_drawer_menu_title_font, R.string.design_drawer_menu_description_font, content);
        addDrawerItem(R.id.item_java_manager, R.drawable.ic_mtrl_java, R.string.design_drawer_menu_title_java, R.string.design_drawer_menu_subtitle_editor_code, content);
        addDrawerItem(R.id.item_resource_manager, R.drawable.ic_mtrl_folder, R.string.design_actionbar_title_manager_resources, R.string.design_drawer_menu_description_view, content);
        addDrawerItem(R.id.item_assets_manager, R.drawable.ic_mtrl_assets, R.string.assets_manager_title, R.string.design_drawer_menu_description_collection, content);
        addDrawerItem(R.id.item_native_libs_manager, R.drawable.ic_mtrl_library, R.string.design_drawer_menu_nativelibs, R.string.design_drawer_menu_nativelibs_subtitle, content);
        addDrawerItem(R.id.item_library_manager, R.drawable.ic_mtrl_component, R.string.design_drawer_menu_title_library, R.string.design_drawer_menu_description_library, content);
        addDrawerItem(R.id.item_appcompat_manager, R.drawable.ic_mtrl_inject, R.string.design_drawer_menu_injection, R.string.design_drawer_menu_injection_subtitle, content);
        addDrawerItem(R.id.item_manifest_manager, R.drawable.ic_mtrl_deployed_code, R.string.design_drawer_menu_androidmanifest, R.string.design_drawer_menu_androidmanifest_subtitle, content);
        addDrawerItem(R.id.item_used_custom_blocks, R.drawable.ic_mtrl_block, R.string.design_drawer_menu_customblocks, R.string.design_drawer_menu_customblocks_subtitle, content);
        addDrawerItem(R.id.item_code_shrinking_manager, R.drawable.ic_mtrl_shield_lock, R.string.design_drawer_menu_proguard, R.string.design_drawer_menu_proguard_subtitle, content);
        addDrawerItem(R.id.item_show_src, R.drawable.ic_mtrl_frame_source, R.string.design_drawer_menu_title_source_code, R.string.design_drawer_menu_description_source_code, content);
        addDrawerItem(R.id.item_xml_command_manager, R.drawable.ic_mtrl_code, R.string.design_drawer_menu_title_xml_command, R.string.design_drawer_menu_description_xml_command, content);
        addDrawerItem(R.id.item_logcat_reader, R.drawable.ic_mtrl_developer_guide, R.string.design_drawer_menu_title_logcat_reader, R.string.design_drawer_menu_subtitle_logcat_reader, content);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.item_view_manager) {
            designActivity.toViewManager();
        } else if (id == R.id.item_image_manager) {
            designActivity.toImageManager();
        } else if (id == R.id.item_sound_manager) {
            designActivity.toSoundManager();
        } else if (id == R.id.item_font_manager) {
            designActivity.toFontManager();
        } else if (id == R.id.item_java_manager) {
            designActivity.toJavaManager();
        } else if (id == R.id.item_resource_manager) {
            designActivity.toResourceManager();
        } else if (id == R.id.item_assets_manager) {
            Intent intent = new Intent(designActivity, ManageAssetsActivity.class);
            intent.putExtra("sc_id", designActivity.sc_id);
            designActivity.startActivity(intent);
        } else if (id == R.id.item_native_libs_manager) {
            Intent intent = new Intent(designActivity, ManageNativelibsActivity.class);
            intent.putExtra("sc_id", designActivity.sc_id);
            designActivity.startActivity(intent);
        } else if (id == R.id.item_library_manager) {
            designActivity.toLibraryManager();
        } else if (id == R.id.item_appcompat_manager) {
            designActivity.toAppCompatInjection();
        } else if (id == R.id.item_manifest_manager) {
            designActivity.toManifestManager();
        } else if (id == R.id.item_used_custom_blocks) {
            designActivity.toCustomBlocks();
        } else if (id == R.id.item_code_shrinking_manager) {
            designActivity.toProguardManager();
        } else if (id == R.id.item_stringfog_manager) {
            designActivity.toStringFogManager();
        } else if (id == R.id.item_show_src) {
            designActivity.toSourceCodeViewer();
        } else if (id == R.id.item_xml_command_manager) {
            designActivity.toXmlCommandManager();
        } else if (id == R.id.item_logcat_reader) {
            designActivity.toLogcatReader();
        }
    }
}
