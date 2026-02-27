{#if addon?.id === 'pano-plugin-avatar'}
  <div class="card">
    <div class="card-header">
      {$_('settings.title')}
    </div>
    <div class="card-body">
      <!-- Max File Size -->
      <div class="row mb-3">
        <label class="col-md-6 col-form-label" for="maxSizeMb">
          <span class="d-block">
            {$_('settings.max-size')}
          </span>
          <small>
            {$_('settings.max-size-desc')}
          </small>
        </label>
        <div class="col-md-6">
          <div class="input-group">
            <input
              type="number"
              class="form-control"
              id="maxSizeMb"
              min="1"
              max="10"
              bind:value={config.maxSizeMb} />
            <span class="input-group-text">MB</span>
          </div>
        </div>
      </div>

      <!-- Allowed Image Types -->
      <div class="row mb-3">
        <div class="col-md-6 col-form-label">
          <span class="d-block">
            {$_('settings.allowed-types')}
          </span>
          <small>
            {$_('settings.allowed-types-desc')}
          </small>
        </div>
        <div class="col-md-6 d-flex align-items-center">
          <div class="d-flex flex-wrap gap-3">
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="type-png"
                checked={config.allowedTypes.includes('image/png')}
                on:change={(e) => toggleType('image/png', e.target.checked)} />
              <label class="form-check-label" for="type-png">PNG</label>
            </div>
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="type-jpeg"
                checked={config.allowedTypes.includes('image/jpeg')}
                on:change={(e) => toggleType('image/jpeg', e.target.checked)} />
              <label class="form-check-label" for="type-jpeg">JPEG</label>
            </div>
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="type-gif"
                checked={config.allowedTypes.includes('image/gif')}
                on:change={(e) => toggleType('image/gif', e.target.checked)} />
              <label class="form-check-label" for="type-gif">GIF</label>
            </div>
          </div>
        </div>
      </div>

      <!-- Allowed Avatar Sources -->
      <div class="row mb-3">
        <div class="col-md-6 col-form-label">
          <span class="d-block">
            {$_('settings.allowed-sources')}
          </span>
          <small>
            {$_('settings.allowed-sources-desc')}
          </small>
        </div>
        <div class="col-md-6 d-flex align-items-center">
          <div class="d-flex flex-wrap gap-3">
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="source-minotar"
                checked={config.allowedSources.includes('MINOTAR')}
                on:change={(e) => toggleSource('MINOTAR', e.target.checked)} />
              <label class="form-check-label" for="source-minotar">Minotar</label>
            </div>
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="source-gravatar"
                checked={config.allowedSources.includes('GRAVATAR')}
                on:change={(e) => toggleSource('GRAVATAR', e.target.checked)} />
              <label class="form-check-label" for="source-gravatar">Gravatar</label>
            </div>
            <div class="form-check">
              <input
                class="form-check-input"
                type="checkbox"
                id="source-custom"
                checked={config.allowedSources.includes('CUSTOM')}
                on:change={(e) => toggleSource('CUSTOM', e.target.checked)} />
              <label class="form-check-label" for="source-custom">{$_('settings.custom-upload')}</label>
            </div>
          </div>
        </div>
      </div>

      <!-- Custom Avatar Sources -->
      <div class="row mb-3">
        <div class="col-md-6 col-form-label">
          <span class="d-block">
            {$_('settings.custom-sources')}
          </span>
          <small>
            {$_('settings.custom-sources-desc')}
          </small>
        </div>
        <div class="col-md-6">
          {#each config.customSources as source, index}
            <div class="card mb-2">
              <div class="card-body p-2">
                <div class="mb-2">
                  <input
                    type="text"
                    class="form-control form-control-sm"
                    placeholder={$_('settings.custom-source-title')}
                    bind:value={source.title} />
                </div>
                <div class="mb-2">
                  <input
                    type="text"
                    class="form-control form-control-sm"
                    placeholder={$_('settings.custom-source-url')}
                    bind:value={source.urlTemplate} />
                  <small class="text-muted">{$_('settings.custom-source-url-hint')}</small>
                </div>
                <div class="d-flex align-items-center justify-content-between">
                  <select class="form-select form-select-sm w-auto" bind:value={source.identifierField}>
                    <option value="username">{$_('settings.identifier-username')}</option>
                    <option value="email">{$_('settings.identifier-email')}</option>
                  </select>
                  <button class="btn btn-sm btn-outline-danger" on:click={() => removeCustomSource(index)} aria-label={$_('generic.remove')}>
                    <i class="fas fa-trash-alt"></i>
                  </button>
                </div>
              </div>
            </div>
          {/each}
          <button class="btn btn-sm btn-outline-primary" on:click={addCustomSource}>
            <i class="fas fa-plus me-1"></i> {$_('settings.add-custom-source')}
          </button>
        </div>
      </div>

      <div class="mt-4">
        <button class="btn btn-secondary" on:click={saveConfig} disabled={saving || !hasChanges}>
          {#if saving}
            <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"
            ></span>
          {:else}{/if}
          {$_('generic.save')}
        </button>
      </div>
    </div>
  </div>
{/if}

<script>
  import {_} from '../../../main.js';
  import ApiUtil from '@panomc/sdk/utils/api';
  import {showToast} from '@panomc/sdk/toasts';

  export let addon;

  let config = addon?.config || {
    maxSizeMb: 1,
    allowedTypes: ['image/png', 'image/jpeg', 'image/gif'],
    allowedSources: ['MINOTAR', 'GRAVATAR', 'CUSTOM'],
    customSources: [],
  };
  let saving = false;

  // Keep track of initial config to determine if there are changes
  let initialConfig = JSON.parse(JSON.stringify(config));

  $: hasChanges = JSON.stringify(config) !== JSON.stringify(initialConfig);

  function toggleType(type, checked) {
    if (checked) {
      if (!config.allowedTypes.includes(type)) {
        config.allowedTypes = [...config.allowedTypes, type];
      }
    } else {
      config.allowedTypes = config.allowedTypes.filter(t => t !== type);
    }
  }

  function toggleSource(source, checked) {
    if (checked) {
      if (!config.allowedSources.includes(source)) {
        config.allowedSources = [...config.allowedSources, source];
      }
    } else {
      config.allowedSources = config.allowedSources.filter(s => s !== source);
    }
  }

  function addCustomSource() {
    config.customSources = [...config.customSources, {
      title: '',
      urlTemplate: '',
      identifierField: 'username'
    }];
  }

  function removeCustomSource(index) {
    config.customSources = config.customSources.filter((_, i) => i !== index);
  }

  async function saveConfig() {
    if (saving) return;
    saving = true;
    try {
      await ApiUtil.put({
        path: '/api/panel/avatar/config',
        body: config,
      });
      addon.config = config;
      initialConfig = JSON.parse(JSON.stringify(config));
      await showToast('components.toasts.settings-save-success');
    } catch (e) {
      console.error('Failed to save avatar config', e);
      await showToast('components.toasts.settings-save-error');
    } finally {
      saving = false;
    }
  }
</script>
