// Module-level pano reference for the theme view: src/main.js sets it in onLoad.
let panoRef = null;

/** @param {any} pano */
export function setPano(pano) {
  panoRef = pano;
}

export function updateAvatarVersion() {
  if (panoRef && panoRef.ui && panoRef.ui.avatar) {
    panoRef.ui.avatar.updateVersion();
  }
}
