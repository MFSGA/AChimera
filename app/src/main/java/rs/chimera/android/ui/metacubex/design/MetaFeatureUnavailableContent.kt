package rs.chimera.android.ui.metacubex.design

import android.widget.LinearLayout
import android.widget.TextView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.layoutInflater

internal fun LinearLayout.addMetaFeatureUnavailableContent() {
    addCategory(R.string.meta_feature_age_key_category)
    addItem(R.string.meta_feature_age_key_x25519)
    addItem(R.string.meta_feature_age_key_hybrid)

    addCategory(R.string.meta_feature_settings_category)
    addItem(R.string.meta_feature_unified_delay)
    addItem(R.string.meta_feature_geodata_mode)
    addItem(R.string.meta_feature_tcp_concurrent)
    addItem(R.string.meta_feature_find_process_mode)

    addCategory(R.string.meta_feature_sniffer_category)
    addItem(R.string.meta_feature_sniffer_strategy)
    addItem(R.string.meta_feature_sniff_http_ports)
    addItem(R.string.meta_feature_sniff_http_override_destination)
    addItem(R.string.meta_feature_sniff_tls_ports)
    addItem(R.string.meta_feature_sniff_tls_override_destination)
    addItem(R.string.meta_feature_sniff_quic_ports)
    addItem(R.string.meta_feature_sniff_quic_override_destination)
    addItem(R.string.meta_feature_force_dns_mapping)
    addItem(R.string.meta_feature_parse_pure_ip)
    addItem(R.string.meta_feature_override_destination)
    addItem(R.string.meta_feature_force_domain)
    addItem(R.string.meta_feature_skip_domain)
    addItem(R.string.meta_feature_skip_src_address)
    addItem(R.string.meta_feature_skip_dst_address)

    addCategory(R.string.meta_feature_geo_files_category)
    addItem(R.string.meta_feature_import_geoip)
    addItem(R.string.meta_feature_import_geosite)
    addItem(R.string.meta_feature_import_country)
    addItem(R.string.meta_feature_import_asn)
}

private fun LinearLayout.addCategory(titleRes: Int) {
    val category = context.layoutInflater.inflate(
        R.layout.meta_feature_preference_category,
        this,
        false,
    ) as TextView
    category.setText(titleRes)
    addView(category)
}

private fun LinearLayout.addItem(titleRes: Int) {
    val item = context.layoutInflater.inflate(
        R.layout.meta_feature_preference_item,
        this,
        false,
    )
    item.findViewById<TextView>(R.id.meta_feature_title).setText(titleRes)
    item.findViewById<TextView>(R.id.meta_feature_summary)
        .setText(R.string.meta_feature_unavailable_summary)
    item.isEnabled = false
    item.isClickable = false
    item.isFocusable = false
    item.alpha = DISABLED_ALPHA
    addView(item)
}

private const val DISABLED_ALPHA = 0.33f
