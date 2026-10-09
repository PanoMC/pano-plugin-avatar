// Plugin-level options of the Pano plugin kit (@panomc/plugin-kit). The namespace is `avatar`
// (the plugin id minus `pano-plugin-`); the view lives where it was, so no file moved.
export default {
  viewDirs: ['src/theme/components/view'],
  styles: {
    // The 80 px preview size of the avatar stays an inline style: a theme rule such as `img { height: auto }` would beat
    // the same size in a layered class and stretch the round preview.
    styleAttrAllow: ['AvatarUpload'],
  },
};
