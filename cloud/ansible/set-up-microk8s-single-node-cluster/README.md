# Setting up the cluster

1. Create `hosts.yml`
    ```yaml
      dev:
        hosts:
          DEV_SERVER_ADDRESS: # e.g. "dev.panel.myorg.com"
            host_is_prod: false
            ansible_user: ubuntu
            ansible_port: 22
      prod:
        hosts:
          PROD_SERVER_ADDRESS: # e.g. "panel.myorg.com"
            host_is_prod: true
            ansible_user: ubuntu
            ansible_port: 4545 # it is a good practice to use a different port than 22
        all:
          - dev
          - prod
    ```
2. Create `steps/02-users/users.vars.yaml`. You can use the attached schema to validate the file. Example:
    ```yaml
    users: [
      {
        name: "kamillapinski",
        groups: [ "admin", "sudo", "users" ],
        microk8s: true,
        prod: true,
        public_key: "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBflTKtM3BFQDGuxTyDPOYx57CnBn8QsbSYbmUBalVLw kamillapinski@Kamils-MacBook-Pro.local"
      }
   ]
    ```
3. Run the playbook:
   ```shell
      ansible-playbook playbook.yml -i hosts.yml
   ```