'use strict';

// Lambda: GET /config
// Legge la configurazione remota da DynamoDB (tabella ugocalc-config) e la restituisce.
// IAM: permesso solo dynamodb:GetItem sulla tabella ugocalc-config.
// Supporta letture non autenticate (globale) e autenticate (per utente specifico).

const { DynamoDBClient, GetItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');
const { verifyToken } = require('../utils/verifyToken');

const TABLE = process.env.CONFIG_TABLE || 'ugocalc-config';
const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
  // Se autenticato, usa userId come PK; altrimenti usa 'global'
  const authHeader = event.headers?.authorization || event.headers?.Authorization;
  const tokenUserId = await verifyToken(authHeader);
  const pk = tokenUserId || 'global';

  const result = await dynamo.send(new GetItemCommand({
    TableName: TABLE,
    Key: marshall({ PK: pk, SK: 'v1' }),
  }));

  if (!result.Item) {
    return response(200, {
      min_benchmark_meals: 30,
      v2_shadow_enabled: false,
      maintenance_message: '',
      force_update_min_version: '',
    });
  }

  const item = unmarshall(result.Item);
  return response(200, {
    min_benchmark_meals: item.minBenchmarkMeals ?? 30,
    v2_shadow_enabled: item.v2ShadowEnabled ?? false,
    maintenance_message: item.maintenanceMessage ?? '',
    force_update_min_version: item.forceUpdateMinVersion ?? '',
  });
};

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}
