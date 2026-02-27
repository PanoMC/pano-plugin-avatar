<div class="row pt-2 pb-3 mb-3 border-bottom animate__animated animate__fadeIn">
  <label class="col-md-4 col-form-label pb-2">
    <div class="d-flex align-items-center gap-2 mb-2">
      <i class="fas fa-user-circle text-secondary"></i>
      <span class="fw-bold">{$_('panel-edit.avatar-title')}</span>
    </div>
    <select class="form-select form-select-sm" bind:value={avatarType}>
      {#each allowedSources as source}
        <option value={source}>
          {source === 'MINOTAR' ? $_('minotar') : source === 'GRAVATAR' ? $_('gravatar') : $_('custom')}
        </option>
      {/each}
    </select>
    <p class="text-secondary small mt-2 mb-0">{$_('panel-edit.avatar-desc', { values: { maxSize: maxSizeMb } })}</p>
  </label>
  
  <div class="col col-form-label">
    <div class="d-flex align-items-center gap-4">
      <div class="position-relative">
        <img
          src={previewSrc}
          alt="Avatar Preview"
          class="rounded-circle border border-2 p-1 bg-white object-fit-cover shadow-sm"
          style="width: 80px; height: 80px;" />
        
        {#if uploading}
          <div class="position-absolute top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center bg-white bg-opacity-75 rounded-circle">
            <div class="spinner-border spinner-border-sm text-primary" role="status"></div>
          </div>
        {/if}
      </div>

      <div class="flex-grow-1">
        {#if avatarType === 'CUSTOM'}
          <div class="vstack gap-2">
            <div class="d-flex gap-2">
              <label for="avatar-input-panel" class="btn btn-sm btn-outline-primary px-3">
                <i class="fas fa-upload me-1"></i> {$_('buttons.upload')}
              </label>
              <input
                type="file"
                id="avatar-input-panel"
                class="d-none"
                accept={allowedTypesAccept}
                on:change={handleFileChange} />

              {#if isDirty}
                <button class="btn btn-sm btn-link text-decoration-none px-0" on:click={resetAvatar}>
                   {$_('buttons.cancel')}
                </button>
              {/if}
            </div>
            {#if selectedFile || (currentFileName && !removeFile)}
              <button class="btn btn-sm btn-link text-danger text-decoration-none p-0 text-start" on:click={removeAvatar}>
                 <i class="fas fa-trash-alt me-1"></i> {$_('buttons.remove')}
              </button>
            {/if}
          </div>
        {:else if avatarType === 'MINOTAR'}
           <div class="text-muted small">
             <i class="fas fa-info-circle me-1"></i>
             {$_('minotar-info', { values: { username: targetUsername || 'user' } })}
           </div>
        {:else if avatarType === 'GRAVATAR'}
           <div class="text-muted small">
             <i class="fas fa-info-circle me-1"></i>
             {$_('gravatar-info', { values: { email: targetEmail || 'email' } })}
           </div>
        {/if}
      </div>
    </div>
  </div>
</div>

<script>
  import { _, updateAvatarVersion } from '../../../main.js';
  import ApiUtil from '@panomc/sdk/utils/api';

  export let playerData;
  export let onHookRegister;

  const defaultPreview = "https://api.dicebear.com/7.x/avataaars/svg?seed=Pano";

  let targetUsername = playerData?.username || '';
  let targetEmail = playerData?.email || '';

  // Config from API
  let config = null;
  let maxSizeMb = 1;
  let allowedSources = ['MINOTAR', 'GRAVATAR', 'CUSTOM'];
  let allowedTypes = ['image/png', 'image/jpeg', 'image/gif'];
  $: allowedTypesAccept = allowedTypes.join(', ');

  // Avatar data
  let initialType = 'MINOTAR';
  let currentFileName = null;
  let avatarType = 'MINOTAR';

  // User-modified state
  let selectedFile = null;
  let selectedFilePreview = null;
  let removeFile = false;
  let uploading = false;

  // Derived values for preview
  $: minotarSrc = `https://minotar.net/avatar/${targetUsername || 'char'}/80`;

  $: previewSrc =
    avatarType === 'MINOTAR' ? minotarSrc :
    avatarType === 'GRAVATAR' ? `https://www.gravatar.com/avatar/000?s=80&d=identicon` :
    selectedFilePreview ? selectedFilePreview :
    currentFileName ? `/api/avatar/image/${currentFileName}` :
    defaultPreview;

  $: isDirty =
    avatarType !== initialType ||
    (avatarType === 'CUSTOM' && (selectedFile !== null || removeFile));

  // Load config and avatar data when mounted
  loadData();

  async function loadData() {
    try {
      const [configRes, avatarRes] = await Promise.all([
        ApiUtil.get({ path: '/api/panel/avatar/config' }),
        ApiUtil.get({ path: `/api/panel/avatar/player/${targetUsername}` }),
      ]);

      if (configRes && !configRes.error) {
        config = configRes;
        maxSizeMb = configRes.maxSizeMb || 1;
        allowedSources = configRes.allowedSources || ['MINOTAR', 'GRAVATAR', 'CUSTOM'];
        allowedTypes = configRes.allowedTypes || ['image/png', 'image/jpeg', 'image/gif'];
      }

      if (avatarRes && !avatarRes.error) {
        initialType = avatarRes.avatarType || 'MINOTAR';
        currentFileName = avatarRes.fileName || null;
        avatarType = initialType;
      }
    } catch (e) {
      console.error('[pano-plugin-avatar] Failed to load data for panel edit', e);
    }
  }

  // Register save handler with the modal — re-registers whenever isDirty changes
  $: if (onHookRegister) {
    onHookRegister({
      _id: 'pano-plugin-avatar-player-edit',
      isDirty: isDirty,
      save: saveAvatar,
    });
  }

  async function saveAvatar() {
    if (!isDirty) return;

    uploading = true;

    try {
      const formData = new FormData();
      formData.append('avatarType', avatarType);

      if (avatarType === 'CUSTOM' && selectedFile) {
        formData.append('avatar', selectedFile);
      }

      const result = await ApiUtil.post({
        path: `/api/panel/avatar/player/${targetUsername}`,
        body: formData,
      });

      if (result && result.error) {
        console.error('[pano-plugin-avatar] Panel save failed:', result.error);
        return;
      }

      // Update initial state after successful save
      initialType = avatarType;

      // Bust avatar caches globally
      updateAvatarVersion();

      if (avatarType === 'CUSTOM') {
        if (selectedFile) {
          // Reload avatar data to get the new filename
          const avatarRes = await ApiUtil.get({
            path: `/api/panel/avatar/player/${targetUsername}`,
          });
          if (avatarRes && !avatarRes.error) {
            currentFileName = avatarRes.fileName;
          }
          selectedFile = null;
          selectedFilePreview = null;
        } else if (removeFile) {
          currentFileName = null;
          removeFile = false;
        }
      } else {
        currentFileName = null;
        selectedFile = null;
        selectedFilePreview = null;
        removeFile = false;
      }
    } catch (e) {
      console.error('[pano-plugin-avatar] Panel save error:', e);
    } finally {
      uploading = false;
    }
  }

  function handleFileChange(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Validate file size
    const maxBytes = maxSizeMb * 1024 * 1024;
    if (file.size > maxBytes) {
      alert($_('toasts.file-too-large', { values: { maxSize: maxSizeMb } }));
      event.target.value = '';
      return;
    }

    // Validate file type
    if (!allowedTypes.includes(file.type)) {
      alert($_('toasts.invalid-type', { values: { types: allowedTypes.map(t => t.split('/')[1].toUpperCase()).join(', ') } }));
      event.target.value = '';
      return;
    }

    selectedFile = file;
    removeFile = false;

    // Generate preview
    const reader = new FileReader();
    reader.onload = (e) => {
      selectedFilePreview = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  function resetAvatar() {
    avatarType = initialType;
    selectedFile = null;
    selectedFilePreview = null;
    removeFile = false;
  }

  function removeAvatar() {
    avatarType = 'MINOTAR';
    selectedFile = null;
    selectedFilePreview = null;
    removeFile = true;
  }
</script>
