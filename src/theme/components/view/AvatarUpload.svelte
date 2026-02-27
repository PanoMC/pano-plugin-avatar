<div class="row pt-2 pb-3 mb-3 border-bottom animate__animated animate__fadeIn">
  <label class="col-md-4 col-form-label pb-2">
    <div class="d-flex align-items-center gap-2 mb-2">
      <i class="fas fa-user-circle text-secondary"></i>
      <span class="fw-bold">{$_('avatar-settings-title')}</span>
    </div>
    <select class="form-select form-select-sm" bind:value={avatarType}>
      {#each allowedSources as source}
        <option value={source}>
          {source === 'MINOTAR' ? $_('minotar') : source === 'GRAVATAR' ? $_('gravatar') : $_('custom')}
        </option>
      {/each}
    </select>
    <p class="text-secondary small mt-2 mb-0">{$_('avatar-upload-requirements', { values: { maxSize: maxSizeMb } })}</p>
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
              <label for="avatar-input" class="btn btn-sm btn-outline-primary px-3">
                <i class="fas fa-upload me-1"></i> {$_('buttons.upload')}
              </label>
              <input
                type="file"
                id="avatar-input"
                class="d-none"
                accept={allowedTypesAccept}
                onchange={handleFileChange} />

              {#if isDirty}
                <button class="btn btn-sm btn-link text-decoration-none px-0" onclick={resetAvatar}>
                   {$_('buttons.cancel')}
                </button>
              {/if}
            </div>
            {#if selectedFile || (currentFileName && !removeFile)}
              <button class="btn btn-sm btn-link text-danger text-decoration-none p-0 text-start" onclick={removeAvatar}>
                 <i class="fas fa-trash-alt me-1"></i> {$_('buttons.remove')}
              </button>
            {/if}
          </div>
        {:else if avatarType === 'MINOTAR'}
           <div class="text-muted small">
             <i class="fas fa-info-circle me-1"></i>
             {$_('minotar-info', { values: { username: session?.user?.username || 'user' } })}
           </div>
        {:else if avatarType === 'GRAVATAR'}
           <div class="text-muted small">
             <i class="fas fa-info-circle me-1"></i>
             {$_('gravatar-info', { values: { email: session?.user?.email || 'email' } })}
           </div>
        {/if}
      </div>
    </div>
  </div>
</div>

<script module>
  import ApiUtil from "@panomc/sdk/utils/api";

  export async function load(event) {
    try {
      const {session: {user}} = await event.parent()
      const [configRes, avatarRes] = await Promise.all([
        ApiUtil.get({ path: '/api/avatar/config', request: event }),
        user
          ? ApiUtil.get({ path: `/api/avatar/user/${user.username}`, request: event })
          : Promise.resolve(null),
      ]);

      return {
        config: configRes && !configRes.error ? configRes : null,
        avatar: avatarRes && !avatarRes.error ? avatarRes : null,
      };
    } catch (e) {
      console.error('[pano-plugin-avatar] Failed to load config/avatar in load method', e);
      return {
        config: null,
        avatar: null,
      };
    }
  }
</script>

<script>
  import { untrack } from 'svelte';
  import { _, updateAvatarVersion } from "../../../main.js";
  import { page } from "@panomc/sdk/svelte";

  let { onRegister, data } = $props();

  const session = $derived($page.data.session);
  const defaultPreview = "https://api.dicebear.com/7.x/avataaars/svg?seed=Pano";

  // Config from API (derived to avoid Svelte 5 state-referenced-locally warning)
  const maxSizeMb = $derived(data?.config?.maxSizeMb || 1);
  const allowedSources = $derived(data?.config?.allowedSources || ['MINOTAR', 'GRAVATAR', 'CUSTOM']);
  const allowedTypes = $derived(data?.config?.allowedTypes || ['image/png', 'image/jpeg', 'image/gif']);
  const allowedTypesAccept = $derived(allowedTypes.join(', '));

  // Avatar data from API — initialized directly from data prop for SSR support
  // (state_referenced_locally warning is intentional: we capture the initial SSR value,
  //  and handle client-side updates via $effect below)
  let initialType = $state(data?.avatar?.avatarType || 'MINOTAR');
  let currentFileName = $state(data?.avatar?.fileName || null);
  let avatarType = $state(data?.avatar?.avatarType || 'MINOTAR');

  // Handle client-side data updates (e.g. re-navigation, prop changes after initial load)
  $effect(() => {
    const avatar = data?.avatar;
    untrack(() => {
      if (avatar) {
        const apiType = avatar.avatarType || 'MINOTAR';
        const apiFile = avatar.fileName || null;

        if (initialType !== apiType) initialType = apiType;
        if (currentFileName !== apiFile) currentFileName = apiFile;

        // Sync user selection only if not modified (not dirty)
        if (!isDirty && avatarType !== apiType) {
          avatarType = apiType;
        }
      }
    });
  });

  // User-modified state
  let selectedFile = $state(null);
  let selectedFilePreview = $state(null);
  let removeFile = $state(false);
  let uploading = $state(false);

  // Derived values for preview
  const minotarSrc = $derived(`https://minotar.net/avatar/${session?.user?.username || 'char'}/80`);
  const gravatarSrc = $derived.by(() => {
    const email = session?.user?.email;
    if (!email) return 'https://www.gravatar.com/avatar/000?s=80&d=identicon';
    // Simple hash placeholder - real hash is done server-side for the actual profile picture
    return `https://www.gravatar.com/avatar/000?s=80&d=identicon`;
  });

  const previewSrc = $derived(
    avatarType === 'MINOTAR' ? minotarSrc :
    avatarType === 'GRAVATAR' ? gravatarSrc :
    selectedFilePreview ? selectedFilePreview :
    currentFileName ? `/api/avatar/image/${currentFileName}` :
    defaultPreview
  );

  const isDirty = $derived(
    avatarType !== initialType ||
    (avatarType === 'CUSTOM' && (selectedFile !== null || removeFile))
  );

  $effect(() => {
    // Explicitly reference isDirty to ensure this effect reruns when the dirty state changes
    const _dirty = isDirty;
    if (onRegister) {
      onRegister({
        get isDirty() { return isDirty },
        save: saveAvatar,
      });
    }
  });

  async function saveAvatar() {
    uploading = true;

    try {
      const formData = new FormData();
      formData.append('avatarType', avatarType);

      if (avatarType === 'CUSTOM' && selectedFile) {
        formData.append('avatar', selectedFile);
      }

      const result = await ApiUtil.post({
        path: '/api/avatar',
        body: formData,
      });

      if (result && result.error) {
        console.error('[pano-plugin-avatar] Save failed:', result.error);
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
            path: `/api/avatar/user/${session.user.username}`,
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
        // Switched away from CUSTOM, server deletes old file
        currentFileName = null;
        selectedFile = null;
        selectedFilePreview = null;
        removeFile = false;
      }

      // Explicitly notify parent to update dirty totals after save
      if (onRegister) {
        onRegister({
          get isDirty() { return isDirty },
          save: saveAvatar,
        });
      }
    } catch (e) {
      console.error('[pano-plugin-avatar] Save error:', e);
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
