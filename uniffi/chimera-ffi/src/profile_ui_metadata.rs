use std::fs;
use std::path::Path;

use yaml_serde::Value;

pub(crate) fn read_ui_subtitle_pattern(config_path: &Path) -> Option<String> {
    let content = fs::read_to_string(config_path).ok()?;
    parse_ui_subtitle_pattern(&content)
}

fn parse_ui_subtitle_pattern(content: &str) -> Option<String> {
    let mut config = yaml_serde::from_str::<Value>(content).ok()?;
    config.apply_merge().ok()?;
    let pattern = config
        .get("clash-for-android")?
        .get("ui-subtitle-pattern")?
        .as_str()?;
    (!pattern.is_empty()).then(|| pattern.to_owned())
}

#[cfg(test)]
mod tests {
    use super::parse_ui_subtitle_pattern;

    #[test]
    fn reads_pattern_from_clash_for_android_section() {
        assert_eq!(
            Some("(?i)香港".to_owned()),
            parse_ui_subtitle_pattern("clash-for-android:\n  ui-subtitle-pattern: '(?i)香港'\n"),
        );
    }

    #[test]
    fn applies_yaml_merge_keys_before_reading_pattern() {
        let config = "\
shared: &shared
  ui-subtitle-pattern: '(?i)香港'
clash-for-android:
  <<: *shared
";

        assert_eq!(
            Some("(?i)香港".to_owned()),
            parse_ui_subtitle_pattern(config)
        );
    }

    #[test]
    fn absent_invalid_or_non_string_pattern_is_ignored() {
        assert_eq!(None, parse_ui_subtitle_pattern("mixed-port: 7890\n"));
        assert_eq!(None, parse_ui_subtitle_pattern("clash-for-android: [\n"));
        assert_eq!(
            None,
            parse_ui_subtitle_pattern("clash-for-android:\n  ui-subtitle-pattern: 42\n"),
        );
        assert_eq!(
            None,
            parse_ui_subtitle_pattern("clash-for-android:\n  ui-subtitle-pattern: ''\n")
        );
    }

    #[test]
    fn preserves_whitespace_in_pattern_expression() {
        assert_eq!(
            Some(" HK".to_owned()),
            parse_ui_subtitle_pattern("clash-for-android:\n  ui-subtitle-pattern: ' HK'\n"),
        );
        assert_eq!(
            Some("  ".to_owned()),
            parse_ui_subtitle_pattern("clash-for-android:\n  ui-subtitle-pattern: '  '\n"),
        );
    }
}
