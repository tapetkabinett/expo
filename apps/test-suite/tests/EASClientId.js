'use strict';

import * as EASClientId from 'expo-eas-client-id';

export const name = 'EASClientId';

export function test(t) {
  t.describe('EASClientId', () => {
    t.it('gets the EAS client ID', async () => {
      const clientId = await EASClientId.getClientIdAsync();
      t.expect(clientId).toBeTruthy();
      const clientId2 = await EASClientId.getClientIdAsync();
      t.expect(clientId).toEqual(clientId2);
    });
  });
}
