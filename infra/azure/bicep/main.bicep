param location string = resourceGroup().location
param environmentName string
param appName string = 'burinake'

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: '${appName}${environmentName}st'
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    allowBlobPublicAccess: false
    minimumTlsVersion: 'TLS1_2'
  }
}

output storageAccountName string = storageAccount.name
