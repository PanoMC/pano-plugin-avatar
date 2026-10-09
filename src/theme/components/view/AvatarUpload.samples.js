// Sample data of AvatarUpload for the view catalogue (doc 02 section 7). Pure data: import only view helpers and
// relative .js fixtures.
/** @type {string[]} */
export const notApplicable = ['error', 'loading'];

const config = {
  maxSizeMb: 2,
  allowedSources: ['MINOTAR', 'GRAVATAR', 'CUSTOM'],
  allowedTypes: ['image/png', 'image/jpeg', 'image/gif'],
};

/** @type {import('@panomc/plugin-kit').Samples} */
export default {
  filled: {
    props: {
      onRegister: null,
      data: { config, avatar: { avatarType: 'CUSTOM', fileName: 'sample.png' } },
    },
  },
  empty: {
    props: {
      onRegister: null,
      data: { config, avatar: null },
    },
  },
};
