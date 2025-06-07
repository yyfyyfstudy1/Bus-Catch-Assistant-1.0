const config = {
  development: {
    apiBaseUrl: '/api',
  },
  production: {
    apiBaseUrl: 'https://api.transport.nsw.gov.au',
  }
}

export default {
  ...config[process.env.NODE_ENV || 'development']
} 