import { PanoPlugin, viewComponent } from '@panomc/sdk';
import { derived } from 'svelte/store';
import { _ as i18n } from '@panomc/sdk/utils/language';
import ApiUtil from '@panomc/sdk/utils/api';

const pluginId = 'pano-plugin-avatar';

// this is to render plugin translations
export const _ = derived(i18n, ($_fn) => {
  return (key, options) => $_fn(`plugins.${pluginId}.${key}`, options);
});

// Module-level pano reference for use by components
let panoRef = null;

export function updateAvatarVersion() {
  if (panoRef && panoRef.ui && panoRef.ui.avatar) {
    panoRef.ui.avatar.updateVersion();
  }
}

export default class PanoAvatarPlugin extends PanoPlugin {
  onLoad() {
    const pano = this.pano;
    panoRef = pano;

    if (pano.isPanel) {
      // Load config when addon detail page opens
      pano.ui.addon.onLoad(async (data, event) => {
        if (data.addon.id !== pluginId) return;

        try {
          const config = await ApiUtil.get({
            path: '/api/panel/avatar/config',
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
      // Register avatar upload component at the top of settings
      pano.ui.settings.cardRows.edit((items) => {
        items.push({
          id: `${pluginId}-upload`,
          component: viewComponent(() => import('./theme/components/view/AvatarUpload.svelte')),
          priority: 110, // Higher than default rows (100, 90, 80)
        });
      });
    }
  }

  onContextUpdate(ctx) { }

  onUnload() { }
}
