import { PanoPlugin, viewComponent } from '@panomc/sdk';
import { derived } from 'svelte/store';
import { api } from '@panomc/sdk/plugin-api';
import { _ as i18n } from '@panomc/sdk/utils/language';
import { showToast } from '@panomc/sdk/toasts';
import { setPano } from './theme/avatar.js';

const pluginId = 'pano-plugin-avatar';

// this is to render plugin translations
export const _ = derived(i18n, ($_fn) => {
  return (key, options) => $_fn(`plugins.${pluginId}.${key}`, options);
});

// Success/failure colouring for this plugin's toasts, matching the panel. showToast from
// @panomc/sdk/toasts is the host panel's ToastContainer `show`, whose signature is
// (text, params, toastComponent, options): passing undefined for toastComponent keeps the
// host's DefaultToast, and options.variant maps to Bootstrap's text-success / text-danger.
// These live here rather than in @panomc/sdk/toasts because this plugin is pinned to
// @panomc/sdk 1.0.0-dev.39, which predates the variants; they can be dropped for a direct
// SDK import once that pin moves. On an older panel build the extra argument is ignored and
// the toast renders neutral, so this degrades instead of breaking.
export function showSuccessToast(text, params = {}) {
  return showToast(text, params, undefined, { variant: 'success' });
}

export function showErrorToast(text, params = {}) {
  return showToast(text, params, undefined, { variant: 'danger' });
}

// The pano object is kept in src/theme/avatar.js so the theme view can ask the host to refresh the avatar.
export { updateAvatarVersion } from './theme/avatar.js';

export default class PanoAvatarPlugin extends PanoPlugin {
  onLoad() {
    const pano = this.pano;
    setPano(pano);

    if (pano.isPanel) {
      // Load config when addon detail page opens
      pano.ui.addon.onLoad(async (data, event) => {
        if (data.addon.id !== pluginId) return;

        try {
          const config = await api.panel.get({
            path: '/avatar/config',
            request: event,
          });
          data.addon.config = config;
        } catch (e) {
          console.error('[pano-plugin-avatar] Failed to load config', e);
        }
      });

      // Register settings component in plugin detail page
      pano.ui.hook.register({
        name: `panel:plugin-detail:content:${pluginId}`,
        component: viewComponent(
          () => import('./panel/components/view/AvatarPluginSettings.svelte'),
        ),
        permission: `pano.plugin.${pluginId}.manage.avatar.settings`,
      });

      // Register avatar edit component in player edit modal
      pano.ui.player.editModal.cardRows.edit((items) => {
        items.push({
          id: `${pluginId}-player-avatar-edit`,
          component: viewComponent(
            () => import('./panel/components/view/PlayerAvatarEdit.svelte'),
          ),
          permission: `pano.plugin.${pluginId}.manage.player.avatar`,
          priority: 110,
        });
      });
    } else {
      // The upload card sits at the top of the settings rows through `export const view` in AvatarUpload.svelte.
    }
  }

  onContextUpdate(ctx) { }

  onUnload() { }
}
