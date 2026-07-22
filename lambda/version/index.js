'use strict';

// Lambda: GET /version
// Legge la versione più recente dell'app da DynamoDB (tabella ugocalc-versions).
// IAM: permesso solo dynamodb:GetItem sulla tabella ugocalc-versions.
// Richiesta globale (non autenticata).

const { DynamoDBClient, GetItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');

const TABLE = process.env.VERSIONS_TABLE || 'ugocalc-versions';
const dynamo = new DynamoDBClient({});

exports.handler = async () => {
  const result = await dynamo.send(new GetItemCommand({
    TableName: TABLE,
    Key: marshall({ PK: 'android', SK: 'latest' }),
  }));

  if (!result.Item) {
    return response(200, {
      latestVersion: '2.4.0',
      minRequiredVersion: '2.4.0',
      changelog: '',
      apkUrl: '',
      mandatory: false,
    });
  }

  const item = unmarshall(result.Item);
  return response(200, {
    latestVersion: item.latestVersion ?? '2.4.0',
    minRequiredVersion: item.minRequiredVersion ?? '2.4.0',
    changelog: item.changelog ?? '',
    apkUrl: item.apkUrl ?? '',
    mandatory: item.mandatory ?? false,
  });
};

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}
